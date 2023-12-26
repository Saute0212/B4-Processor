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
  io.memory.request.bits.burstLength := (params.MemoryBurstLength-1).U

  io.memory.request.valid := io.fetch.request.valid

  //アドレスを格納
  val addr = WireInit(UInt(64.W), 0.U)
  when(io.fetch.request.valid)
  {
    addr := io.fetch.request.bits
    io.memory.request.bits.address := io.fetch.request.bits(63, 6) ## 0.U(6.W)
    when(io.memory.request.ready)
    {
      io.fetch.request.ready := true.B
    }
  }

  //タグ・インデックス・オフセットの抽出
  val IgnoreBits = log2Up(16)
  val OffsetBits = log2Up(params.ICacheDataNum)
  val IndexBits = log2Up(params.ICacheSet)
  val TagBits = 64 - IndexBits - OffsetBits - IgnoreBits
  val AddrOffset = addr(IgnoreBits + OffsetBits - 1, IgnoreBits)
  val AddrOffsetReg = RegInit(0.U(OffsetBits.W))
  val AddrIndex = addr(IgnoreBits + OffsetBits + IndexBits - 1, IgnoreBits + OffsetBits)
  val AddrIndexReg = RegInit(0.U(IndexBits.W))
  val AddrTag = addr(63, IgnoreBits + OffsetBits + IndexBits)
  val AddrTagReg = RegInit(0.U(TagBits.W))

  //アドレスをタグ・インデックス・オフセットに分け、レジスタに格納
  when(io.fetch.request.valid) {
    AddrOffsetReg := AddrOffset
    AddrIndexReg := AddrIndex
    AddrTagReg := AddrTag
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

/*
  io.fetch.request.ready := false.B
  io.fetch.response.bits := DontCare
  io.fetch.response.valid := false.B

  io.memory.request.valid := false.B
  io.memory.request.bits := DontCare
  io.memory.response.ready := false.B

  io.memory.request.bits.size := MemoryAccessWidth.DoubleWord
  io.memory.request.bits.burstLength := (params.MemoryBurstLength-1).U

  io.memory.request.valid := io.fetch.request.valid
  io.fetch.request.ready := io.memory.request.ready

  //アドレスを格納
  val addr = WireInit(UInt(64.W), 0.U)
  addr := io.fetch.request.bits

  //タグ・インデックス・オフセットの抽出
  val IgnoreBits = log2Up(16)
  val OffsetBits = log2Up(params.ICacheDataNum)
  val IndexBits = log2Up(params.ICacheSet)
  val TagBits = 64 - IndexBits - OffsetBits - IgnoreBits
  val AddrOffset = addr(IgnoreBits + OffsetBits - 1, IgnoreBits)
  val AddrOffsetReg = RegInit(0.U(OffsetBits.W))
  val AddrIndex = addr(IgnoreBits + OffsetBits + IndexBits - 1, IgnoreBits + OffsetBits)
  val AddrIndexReg = RegInit(0.U(IndexBits.W))
  val AddrTag = addr(63, IgnoreBits + OffsetBits + IndexBits)
  val AddrTagReg = RegInit(0.U(TagBits.W))

  //有効ビット・タグ・インデックス
  val ICacheValidBit = RegInit(VecInit(Seq.fill(params.ICacheWay)(VecInit(Seq.fill(params.ICacheSet)(false.B)))))
  val ICacheTag = Seq.fill(params.ICacheWay)(SyncReadMem(params.ICacheSet, UInt(TagBits.W)))
  val ICacheDataBlock = Seq.fill(params.ICacheWay)(SyncReadMem(params.ICacheSet, UInt(params.ICacheBlockWidth.W)))

  //バースト転送のカウンター
  val count = RegInit(0.U(8.W))

  //ウェイのカウンター(各セットごとにカウンターを用意)
  val SelectWay = RegInit(VecInit(Seq.fill(params.ICacheSet)(0.U(1.W))))

  //アドレスをタグ・インデックス・オフセットに分け、レジスタに格納
  when(io.fetch.request.valid) {
    AddrOffsetReg := AddrOffset
    AddrIndexReg := AddrIndex
    AddrTagReg := AddrTag
  }

  //ヒットしたかどうか判定
  val hitVec = RegInit(VecInit(Seq.fill(params.ICacheWay)(false.B)))
  val hitWayNum = RegInit(0.U(log2Up(params.ICacheWay).W))
 
  for(i <- 0 until params.ICacheWay)
  {
    hitVec(i) := ICacheValidBit(i)(AddrIndexReg) && ICacheTag(i).read(AddrIndexReg) === AddrTagReg
    when(hitVec(i))
    {
      hitWayNum := i.U
    }
  }

  val hit = hitVec.reduce(_ || _)

  //BRAMからRead
  val ReadData = MuxLookup(hitWayNum, 0.U)((0 until params.ICacheWay).map(i => i.U -> ICacheDataBlock(i).read(AddrIndexReg)))

  when(RegNext(hit, false.B))
  {
    //ヒットした場合
    val DataHitOut = MuxLookup(AddrOffsetReg, 0.U)(
        (0 until params.ICacheDataNum).map(i => i.U -> ReadData((params.ICacheBlockWidth / params.ICacheDataNum) * (i + 1) - 1, (params.ICacheBlockWidth / params.ICacheDataNum) * i))
    )
    
    //fetchへリターン
    io.fetch.response.valid := true.B
    io.fetch.response.bits := DataHitOut
  } .otherwise {
    //ミスした場合
    io.memory.request.bits.address := addr(63, 6) ## 0.U(6.W)

    val ReadDataBuf = RegInit(VecInit(Seq.fill(params.MemoryBurstLength)(0.U(64.W))))
    io.memory.response.ready := true.B

    //メモリからのデータ(64bit)をReadDataBufに格納
    when(io.memory.response.valid) {
      ReadDataBuf(count) := io.memory.response.bits.value
      count := count + 1.U
    }

    //バースト転送終了時実行
    when(count === 8.U)
    {
      io.memory.response.ready := false.B

      //データをリトルエンディアンでReadDataComへ格納
      val ReadDataCom = Cat(ReadDataBuf.reverse)

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
        ICacheDataBlock(0).write(AddrIndexReg, ReadDataCom)
        ICacheTag(0).write(AddrIndexReg, AddrTagReg)
        ICacheValidBit(0)(AddrIndexReg) := true.B
        count := 0.U
      } .otherwise {
        //ウェイ1に書き込み
        ICacheDataBlock(1).write(AddrIndexReg, ReadDataCom)
        ICacheTag(1).write(AddrIndexReg, AddrTagReg)
        ICacheValidBit(1)(AddrIndexReg) := true.B
        count := 0.U
      }
    }
  }
*/

/*

  io.fetch.request.ready := false.B
  io.fetch.response.bits := DontCare
  io.fetch.response.valid := false.B
  
  io.memory.request.valid := false.B
  io.memory.request.bits := DontCare
  io.memory.response.ready := false.B

  io.memory.request.bits.size := MemoryAccessWidth.DoubleWord
  io.memory.request.bits.burstLength := (params.MemoryBurstLength-1).U

  io.memory.request.valid := io.fetch.request.valid
  io.fetch.request.ready := io.memory.request.ready

  //アドレスを格納
  val addr = WireInit(UInt(64.W), 0.U)
  addr := io.fetch.request.bits

  //タグ・インデックス・オフセットの抽出
  val IgnoreBits = log2Up(16)
  val OffsetBits = log2Up(params.ICacheDataNum)
  val IndexBits = log2Up(params.ICacheSet)
  val TagBits = 64 - IndexBits - OffsetBits - IgnoreBits
  val AddrOffset = addr(IgnoreBits + OffsetBits - 1, IgnoreBits)
  val AddrOffsetReg = RegInit(0.U(OffsetBits.W))
  val AddrIndex = addr(IgnoreBits + OffsetBits + IndexBits - 1, IgnoreBits + OffsetBits)
  val AddrIndexReg = RegInit(0.U(IndexBits.W))
  val AddrTag = addr(63, IgnoreBits + OffsetBits + IndexBits)
  val AddrTagReg = RegInit(0.U(TagBits.W))
  
  when(io.fetch.request.valid) {
    AddrOffsetReg := AddrOffset
    AddrIndexReg := AddrIndex
    AddrTagReg := AddrTag
  }

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
  val hitWayNum = RegInit(0.U(log2Up(params.ICacheWay).W))
  for(i <- 0 until params.ICacheWay)
  {
    hitVec(i) := ICacheValidBit(i)(AddrIndex) &&  ICacheTag(i).read(AddrIndex) === AddrTag
    when(hitVec(i))
    {
      hitWayNum := i.U
    }
  }

  // val hit = hitVec.reduce(_ || _)

  //BRAMからRead
  val ReadData = MuxLookup(hitWayNum, 0.U)((0 until params.ICacheWay).map(i => i.U -> ICacheDataBlock(i).read(AddrIndex)))

  //fetchへのリターンの内容を接続
  val DataResponse = WireInit(UInt((params.ICacheBlockWidth / params.ICacheDataNum).W), 0.U)

  //ヒットしたかどうか判定
  when(hitVec.reduce(_ || _)) {
    /**
     * ヒットした場合
     */
    val DataHitOut = MuxLookup(AddrOffsetReg, 0.U)(
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
    io.memory.request.bits.address := addr(63, 6) ## 0.U(6.W)

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
      
      /**
        * キャッシュへ書き込み
        * 有効ビット：false -> true
        * タグ：AddrIndexReg
        * データブロック：DataResponse
        * ウェイの選択は"0"と"1"に順番に書き込む
        */

      //ウェイの選択を行い、キャッシュへ書き込む
      SelectWay(AddrIndexReg) := SelectWay(AddrIndexReg) + 1.U
      // MuxLookup(SelectWay(AddrIndexReg), 0.U)((0 until params.ICacheWay).map(i => i.U -> ICacheDataBlock(i).write(AddrIndexReg, ReadDataCom)))
      // MuxLookup(SelectWay(AddrIndexReg), 0.U)((0 until params.ICacheWay).map(i => i.U -> ICacheTag(i).write(AddrIndexReg, AddrTagReg)))
      // MuxLookup(SelectWay(AddrIndexReg), 0.U)((0 until params.ICacheWay).map(i => i.U -> ICacheValidBit(i)(AddrIndexReg) := true.B))
  //  val ReadData = MuxLookup(hitWayNum, 0.U)((0 until params.ICacheWay).map(i => i.U -> ICacheDataBlock(i).read(AddrIndex)))
      // when(SelectWay(AddrIndexReg) === 0.U)
      // {
      //   //ウェイ0に書き込み
      //   ICacheDataBlock(0).write(AddrIndexReg, ReadDataCom)
      //   ICacheTag(0).write(AddrIndexReg, AddrTagReg)
      //   ICacheValidBit(0)(AddrIndexReg) := true.B
      // } .elsewhen(SelectWay(AddrIndexReg) === 1.U) {
      //   //ウェイ1に書き込み
      //   ICacheDataBlock(1).write(AddrIndexReg, ReadDataCom)
      //   ICacheTag(1).write(AddrIndexReg, AddrTagReg)
      //   ICacheValidBit(1)(AddrIndexReg) := true.B
      // }

      //ReadDataComのOffset番目をDataMissOutへ出力(128bitのデータ)
      val DataMissOut = MuxLookup(AddrOffsetReg, 0.U)(
          (0 until params.ICacheDataNum).map(i => i.U -> ReadDataCom((params.ICacheBlockWidth / params.ICacheDataNum) * (i + 1) - 1, (params.ICacheBlockWidth / params.ICacheDataNum) * i))
      )
      DataResponse := DataMissOut
      ReturnFlag := 1.U
    }
  }

  //fetchへリターン
  when(ReturnFlag === 1.U) {
    io.fetch.response.valid := true.B
    io.fetch.response.bits := DataResponse
  }
  
  //countとReturnFlagを0に初期化
  when(io.fetch.response.ready) {
    count := 0.U
    ReturnFlag := 0.U
  }
*/

/*
  io.fetch.request.ready := false.B
  io.fetch.response.bits := DontCare
  io.fetch.response.valid := false.B

  io.memory.request.valid := false.B
  io.memory.request.bits := DontCare
  io.memory.response.ready := false.B

  io.memory.request.bits.size := MemoryAccessWidth.DoubleWord
  io.memory.request.bits.burstLength := (params.MemoryBurstLength-1).U

  io.memory.request.valid := io.fetch.request.valid
  io.fetch.request.ready := io.memory.request.ready

  //アドレスを格納
  val addr = WireInit(UInt(64.W), 0.U)
  addr := io.fetch.request.bits

  //タグ・インデックス・オフセットの抽出
  val IgnoreBits = log2Up(16)
  val OffsetBits = log2Up(params.ICacheDataNum)
  val IndexBits = log2Up(params.ICacheSet)
  val TagBits = 64 - IndexBits - OffsetBits - IgnoreBits
  val AddrOffset = addr(IgnoreBits + OffsetBits - 1, IgnoreBits)
  val AddrOffsetReg = RegInit(0.U(OffsetBits.W))
  val AddrIndex = addr(IgnoreBits + OffsetBits + IndexBits - 1, IgnoreBits + OffsetBits)
  val AddrIndexReg = RegInit(0.U(IndexBits.W))
  val AddrTag = addr(63, IgnoreBits + OffsetBits + IndexBits)
  val AddrTagReg = RegInit(0.U(TagBits.W))

  //アドレスをタグ・インデックス・オフセットに分け、レジスタに格納
  when(io.fetch.request.valid) {
    AddrOffsetReg := AddrOffset
    AddrIndexReg := AddrIndex
    AddrTagReg := AddrTag
  }

  //有効ビット・タグ・インデックス
  val ICacheValidBit = RegInit(VecInit(Seq.fill(params.ICacheWay)(VecInit(Seq.fill(params.ICacheSet)(false.B)))))
  val ICacheTag = Seq.fill(params.ICacheWay)(SyncReadMem(params.ICacheSet, UInt(TagBits.W)))
  val ICacheDataBlock = Seq.fill(params.ICacheWay)(SyncReadMem(params.ICacheSet, UInt(params.ICacheBlockWidth.W)))

  //バースト転送のカウンター
  val count = RegInit(0.U(8.W))

  //ウェイのカウンター(各セットごとにカウンターを用意)
  val SelectWay = RegInit(VecInit(Seq.fill(params.ICacheSet)(0.U(1.W))))

  //バースト転送された際のデータバッファ
  val ReadDataBuf = RegInit(VecInit(Seq.fill(params.MemoryBurstLength)(0.U(64.W))))

  //ヒットしたかどうか判定
  val hitVec = RegInit(VecInit(Seq.fill(params.ICacheWay)(false.B)))
  val hitWayNum = RegInit(0.U(log2Up(params.ICacheWay).W))
 
  for(i <- 0 until params.ICacheWay)
  {
    hitVec(i) := ICacheValidBit(i)(AddrIndexReg) && ICacheTag(i).read(AddrIndexReg) === AddrTagReg
    when(hitVec(i))
    {
      hitWayNum := i.U
    }
  }

  val hit = hitVec.reduce(_ || _)
  
  //BRAMからRead
  val ReadData = MuxLookup(hitWayNum, 0.U)((0 until params.ICacheWay).map(i => i.U -> ICacheDataBlock(i).read(AddrIndex)))
  
  when(RegNext(hit, false.B))
  {
    //ヒットした場合
    val DataHitOut = MuxLookup(AddrOffsetReg, 0.U)(
        (0 until params.ICacheDataNum).map(i => i.U -> ReadData((params.ICacheBlockWidth / params.ICacheDataNum) * (i + 1) - 1, (params.ICacheBlockWidth / params.ICacheDataNum) * i))
    )
    
    //fetchへリターン
    io.fetch.response.valid := true.B
    io.fetch.response.bits := DataHitOut
  } .otherwise {
    //ミスした場合
    io.memory.request.bits.address := addr(63, 6) ## 0.U(6.W)
    io.memory.response.ready := true.B

    //メモリからのデータ(64bit)をReadDataBufに格納
    when(io.memory.response.valid) {
      ReadDataBuf(count) := io.memory.response.bits.value
      count := count + 1.U
    }

    when(count === 8.U)
    {
      io.memory.response.ready := false.B

      //データをリトルエンディアンでReadDataComへ格納
      val ReadDataCom = Cat(ReadDataBuf.reverse)

      //ウェイの選択を行い、キャッシュへ書き込む
      SelectWay(AddrIndexReg) := SelectWay(AddrIndexReg) + 1.U
      when(SelectWay(AddrIndexReg) === 0.U)
      {
        //ウェイ0に書き込み
        ICacheDataBlock(0).write(AddrIndexReg, ReadDataCom)
        ICacheTag(0).write(AddrIndexReg, AddrTagReg)
        ICacheValidBit(0)(AddrIndexReg) := true.B
        count := 0.U
      } .otherwise {
        //ウェイ1に書き込み
        ICacheDataBlock(1).write(AddrIndexReg, ReadDataCom)
        ICacheTag(1).write(AddrIndexReg, AddrTagReg)
        ICacheValidBit(1)(AddrIndexReg) := true.B
        count := 0.U
      }

      val DataMissOut = MuxLookup(AddrOffsetReg, 0.U)(
          (0 until params.ICacheDataNum).map(i => i.U -> Cat(ReadDataBuf.reverse)((params.ICacheBlockWidth / params.ICacheDataNum) * (i + 1) - 1, (params.ICacheBlockWidth / params.ICacheDataNum) * i))
      )

      //fetchへリターン
      io.fetch.response.valid := true.B
      io.fetch.response.bits := DataMissOut
    }
  }
*/