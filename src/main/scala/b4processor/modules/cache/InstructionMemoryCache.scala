package b4processor.modules.cache

import b4processor.Parameters
import b4processor.connections.InstructionCache2Fetch
import b4processor.modules.memory.{InstructionResponse, MemoryReadChannel, MemoryReadRequest}
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import b4processor.structures.memoryAccess.MemoryAccessWidth

/** 命令キャッシュモジュール
 *
 * とても単純なキャッシュ機構
 */
class InstructionMemoryCache(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {

    /** フェッチ */
    val fetch = new Bundle {
      val request = Flipped(Decoupled(UInt(64.W)))
      val response = Decoupled(UInt(128.W))
    }

    val memory = new MemoryReadChannel()

    val threadId = Input(UInt(log2Up(params.threads).W))
  })

  io.fetch.request.ready := false.B
  io.fetch.response.bits := DontCare
  io.fetch.response.valid := false.B

  io.memory.request.valid := false.B
  io.memory.request.bits := DontCare
  io.memory.response.ready := false.B

  io.memory.request.bits.size := MemoryAccessWidth.DoubleWord
  io.memory.request.bits.burstLength := 7.U

  //アドレスを格納
  val addr = WireInit(UInt(64.W), 0.U)
  addr := io.fetch.request.bits

  //タグ・インデックス・オフセットの抽出
  val IgnoreBits = log2Up(16)
  val OffsetBits = log2Up(params.ICacheDataNum)
  val IndexBits = log2Up(params.ICacheSet)
  val TagBits = 63 - IndexBits - OffsetBits - IgnoreBits
  val AddrOffset = addr(IgnoreBits + OffsetBits - 1, IgnoreBits)
  val AddrOffsetReg = RegInit(0.U(OffsetBits.W))
  val AddrIndex = addr(IgnoreBits + OffsetBits + IndexBits - 1, IgnoreBits + OffsetBits)
  val AddrIndexReg = RegInit(0.U(IndexBits.W))
  val AddrTag = addr(63, IgnoreBits + OffsetBits + IndexBits)
  val AddrTagReg = RegInit(0.U(TagBits.W))

  //有効ビット・タグ・インデックス
  val ICacheValidBit = RegInit(VecInit(Seq.fill(params.ICacheWay)(VecInit(Seq.fill(params.ICacheSet)(false.B)))))
  val ICacheTag = Seq.fill(params.ICacheWay)(SyncReadMem(params.ICacheSet, UInt((TagBits + 1).W)))
  val ICacheDataBlock = Seq.fill(params.ICacheWay)(SyncReadMem(params.ICacheSet, UInt(params.ICacheBlockWidth.W)))

  //バースト転送のカウンター
  val count = RegInit(0.U(8.W))
  
  //ウェイのカウンター(各セットごとにカウンターを用意)
  val SelectWay = RegInit(VecInit(Seq.fill(params.ICacheSet)(1.U(1.W))))

  //ヒットしたかどうか判定
  val hitVec = WireInit(VecInit(Seq.fill(params.ICacheWay)(false.B)))
  val ReturnFlag = RegInit(0.U(1.W))
  var hitWayNum = 0
  when(ReturnFlag === 0.U) {
    for (i <- 0 until params.ICacheWay) {
      hitVec(i) := ICacheValidBit(i)(AddrIndex) && ICacheTag(i).read(AddrIndex) === AddrTag
      //ヒットしたWayを記録
      when(hitVec(i)) {
        hitWayNum = i
      }
    }
  }

  val hit = hitVec.reduce(_ || _)

  //BRAMからRead
  val ReadData = ICacheDataBlock(hitWayNum).read(AddrIndex)

  //fetchへのリターンの内容を接続
  val DataResponse = WireInit(UInt((params.ICacheBlockWidth / params.ICacheDataNum).W), 0.U)

  //ヒットしたかどうか判定
  when(RegNext(hit, false.B)) {
    /**
     * ヒットした場合
     */
    val DataHitOut = MuxLookup(AddrOffset, 0.U)(
      (0 until params.ICacheDataNum).map(i => i.U -> ReadData((params.ICacheBlockWidth / params.ICacheDataNum) * (i + 1) - 1, (params.ICacheBlockWidth / params.ICacheDataNum) * i))
    )
    DataResponse := DataHitOut
    ReturnFlag := 1.U
  } .otherwise {
    /**
     * ミスした場合
     * DRAMから読み込む
     * fetchへリターン
     * キャッシュへ書き込む
     */

    //アドレスを送信
    io.memory.request.valid := io.fetch.request.valid
    io.fetch.request.ready := io.memory.request.ready
    when(io.fetch.request.valid) {
      AddrOffsetReg := AddrOffset
      AddrIndexReg := AddrIndex
      AddrTagReg := AddrTag
    }

    val MemAddr = addr(63, 6) ## 0.U(6.W)
    io.memory.request.bits.address := MemAddr

    //データを受信
    val ReadDataBuf = RegInit(VecInit(Seq.fill(8)(0.U(64.W))))
    io.memory.response.ready := true.B

    //メモリからのデータ(64bit)をReadDataBufに格納
    when(io.memory.response.valid) {
      ReadDataBuf(count) := io.memory.response.bits.value
      count := count + 1.U
    }

    //バースト転送終了時実行
    when(count === 8.U) {
      io.memory.response.ready := false.B

      //データをリトルエンディアンでReadDataComへ格納
      val ReadDataCom = Cat(ReadDataBuf.reverse)

      //ReadDataComのOffset番目をDataMissOutへ出力(128bitのデータ)
      val DataMissOut = MuxLookup(AddrOffsetReg, 0.U)(
          (0 until params.ICacheDataNum).map(i => i.U -> ReadDataCom((params.ICacheBlockWidth / params.ICacheDataNum) * (i + 1) - 1, (params.ICacheBlockWidth / params.ICacheDataNum) * i))
      )

      DataResponse := DataMissOut
      ReturnFlag := 1.U

      /**
        * キャッシュへ書き込み
        * 有効ビット：false -> true
        * タグ：AddrIndexReg
        * データブロック：DataResponse
        * ウェイの選択は"0"と"1"に順番に書き込む
        */
      
      //ウェイの選択を行い、キャッシュへ書き込む
      SelectWay(AddrIndexReg) := SelectWay(AddrIndexReg) + 1.U
      when(SelectWay(AddrIndexReg) === 0.U)
      {
        //ウェイ0に書き込み
        ICacheDataBlock(0).write(AddrIndexReg, DataResponse)
        ICacheTag(0).write(AddrIndexReg, AddrTagReg)
        RegNext(ICacheValidBit(0)(AddrIndexReg)) := true.B
      } .otherwise {
        //ウェイ1に書き込み
        ICacheDataBlock(1).write(AddrIndexReg, DataResponse)
        ICacheTag(1).write(AddrIndexReg, AddrTagReg)
        RegNext(ICacheValidBit(1)(AddrIndexReg)) := true.B
      }
    }
  }

  //fetchへリターン
  when(ReturnFlag === 1.U) {
    io.fetch.response.valid := true.B
    io.fetch.response.bits := DataResponse
    when(RegNext(io.fetch.response.ready)) {
      io.fetch.response.valid := false.B
    }
  }

  //countとReturnFlagを0に初期化
  when(io.fetch.response.ready) {
    count := 0.U
    ReturnFlag := 0.U
  }
}

object InstructionMemoryCache extends App {
  implicit val params = Parameters()
  ChiselStage.emitSystemVerilogFile(new InstructionMemoryCache)
}

/*
  === Test Result : 2023/12/09 02:40 ===
  Total Test : 31
  Succeeded  : 15
  Failed     : 16
*/

/*
  |   [Requset]     |     |   [Requset]     |
  |   Valid         |     |   Valid         |
  |  ------------>  |     |  ------------>  |
  |   Address       |     |   Address       |
  |  ------------>  |     |  ------------>  |
F |   Ready         |  I  |   Ready         | M
e |  <------------  |  C  |  <------------  | e
t |=================|  a  |=================| m
c |   Valid         |  c  |   Valid         | o
h |  <------------  |  h  |  <------------  | r
  |   Data          |  e  |   Data          | y
  |  <------------  |     |  <------------  |
  |   Ready         |     |   Ready         |
  |  ------------>  |     |  ------------>  |
  |   [Response]    |     |   [Response]    |
*/
