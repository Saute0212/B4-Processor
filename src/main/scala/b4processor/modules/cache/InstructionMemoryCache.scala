package b4processor.modules.cache

import b4processor.Parameters
import b4processor.connections.InstructionCache2Fetch
import b4processor.modules.memory.{
  InstructionResponse,
  MemoryReadChannel,
  MemoryReadRequest,
}
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

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

  //アドレスを格納
  val addr = WireInit(UInt(64.W), 0.U)
  addr := io.fetch.request.bits
  when(RegNext(io.fetch.request.valid))
  {
    // addr := io.fetch.request.bits
    io.fetch.request.ready := true.B
  }

  //タグ・インデックス・オフセットの抽出
  val IgnoreBits = log2Up(16)
  val OffsetBits = log2Up(params.ICacheDataNum)
  val IndexBits = log2Up(params.ICacheSet)
  val TagBits = 63 - IndexBits - OffsetBits - IgnoreBits
  val AddrOffset = addr(IgnoreBits + OffsetBits -1, IgnoreBits)
  val AddrIndex = addr(IgnoreBits + OffsetBits + IndexBits -1, IgnoreBits + OffsetBits)
  val AddrTag = addr(63, IgnoreBits + OffsetBits + IndexBits)

  //有効ビット・タグ・インデックス
  val ICacheValidBit = RegInit(VecInit(Seq.fill(params.ICacheWay)(VecInit(Seq.fill(params.ICacheSet)(false.B)))))
  val ICacheTag = Seq.fill(params.ICacheWay)(SyncReadMem(params.ICacheSet, UInt((TagBits+1).W)))
  val ICacheDataBlock = Seq.fill(params.ICacheWay)(SyncReadMem(params.ICacheSet, UInt(params.ICacheBlockWidth.W)))

  //ヒットしたかどうか判定
  val hitVec = WireInit(VecInit(Seq.fill(params.ICacheWay)(false.B)))
  val ReturnFlag = RegInit(0.U(1.W))
  var hitWayNum = 0
  when(ReturnFlag === 0.U)
  {
    for(i <- 0 until params.ICacheWay)
    {
      hitVec(i) := ICacheValidBit(i)(AddrIndex) && ICacheTag(i).read(AddrIndex) === AddrTag
      //ヒットしたWayを記録
      when(hitVec(i))
      {
        hitWayNum = i
      }
    }
  }

  val hit = hitVec.reduce(_ || _)

  //BRAMからRead
  val ReadData = ICacheDataBlock(hitWayNum).read(AddrIndex)

  val DataResponse = WireInit(UInt((params.ICacheBlockWidth / params.ICacheDataNum).W), 0.U)

  // val ReturnFlag = RegInit(0.U(1.W))
  val count = RegInit(0.U(8.W))

  when(RegNext(hit))
  {
    /**
      * ヒットした場合
      */
    val DataHitOut = MuxLookup(AddrOffset, 0.U)(
        (0 until params.ICacheDataNum).map(i => i.U -> ReadData((params.ICacheBlockWidth / params.ICacheDataNum)*(i+1)-1, (params.ICacheBlockWidth / params.ICacheDataNum)*i))
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
      io.memory.request.valid := true.B
      val MemAddr = RegNext(addr(63, 6)##0.U(6.W))
      io.memory.request.bits.address := MemAddr
      io.memory.request.bits.burstLength := 7.U
      when(RegNext(io.memory.request.ready))
      {
        io.memory.request.valid := false.B
      }


    //データを受信
    val ReadDataBuf = RegInit(VecInit(Seq.fill(8)(0.U(64.W))))
    when(RegNext(io.memory.request.ready))
    {
      io.memory.response.ready := true.B
      
      //メモリからのデータ(64bit)をReadDataBufに格納
      when(io.memory.response.valid)
      {
        ReadDataBuf(count) := io.memory.response.bits.value
        count := count + 1.U
      }

      when(RegNext(count) === 8.U)
      {
        io.memory.response.ready := false.B
        val ReadDataCom = Cat(ReadDataBuf.reverse)
        val DataMissOut = MuxLookup(AddrOffset, 0.U)(
            (0 until params.ICacheDataNum).map(i => i.U -> ReadDataCom((params.ICacheBlockWidth / params.ICacheDataNum)*(i+1)-1, (params.ICacheBlockWidth / params.ICacheDataNum)*i))
        )
        DataResponse := DataMissOut
        ReturnFlag := 1.U

        /**
          * キャッシュへ書き込み
          * 有効ビット：true
          * タグ：AddrIndex
          * データブロック：ReadDataCom
          * ウェイの選択は"0"と"1"に順番に書き込む
        //   */
        // ICacheTag(0).write(AddrIndex, AddrTag)
        // ICacheDataBlock(0).write(AddrIndex, ReadDataCom)
        // ICacheValidBit(0)(AddrIndex) := true.B
      }
    }
  }

  //fetchへリターン
  when(ReturnFlag === 1.U)
  {
    io.fetch.response.valid := true.B
    io.fetch.response.bits := DataResponse
    when(RegNext(io.fetch.response.ready))
    {
      io.fetch.response.valid :=false.B
    }
  }

  when(io.fetch.response.ready)
  {
    count := 0.U
    ReturnFlag := 0.U
  }
}

object InstructionMemoryCache extends App {
  implicit val params = Parameters()
  ChiselStage.emitSystemVerilogFile(new InstructionMemoryCache)
}

/*
io.memory.request.ready
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
  io.fetch.response.valid := false.B
  io.fetch.request.ready := false.B
  io.fetch.response.bits := DontCare
  io.memory.request.bits := DontCare
  io.memory.request.valid := false.B
  io.memory.response.ready := false.B

  //アドレスを格納
  val addr = WireInit(UInt(64.W), 0.U)
  when(io.fetch.request.valid)
  {
    addr := io.fetch.request.bits
    io.fetch.request.ready := true.B
  }

  //タグ・インデックス・オフセットの抽出
  val IgnoreBits = log2Up(16)
  val OffsetBits = log2Up(params.ICacheDataNum)
  val IndexBits = log2Up(params.ICacheSet)
  val TagBits = 63 - IndexBits - OffsetBits - IgnoreBits
  val AddrOffset = addr(IgnoreBits + OffsetBits -1, IgnoreBits)
  val AddrIndex = addr(IgnoreBits + OffsetBits + IndexBits -1, IgnoreBits + OffsetBits)
  val AddrTag = addr(63, IgnoreBits + OffsetBits + IndexBits)

  //有効ビット・タグ・インデックス
  val ICacheValidBit = RegInit(VecInit(Seq.fill(params.ICacheWay)(VecInit(Seq.fill(params.ICacheSet)(false.B)))))
  val ICacheTag = Seq.fill(params.ICacheWay)(SyncReadMem(params.ICacheSet, UInt((TagBits+1).W)))
  val ICacheDataBlock = Seq.fill(params.ICacheWay)(SyncReadMem(params.ICacheSet, UInt(params.ICacheBlockWidth.W)))

  //ヒットしたかどうか判定
  val hitVec = WireInit(VecInit(Seq.fill(params.ICacheWay)(false.B)))
  var hitWayNum = 0
  for(i <- 0 until params.ICacheWay)
  {
    hitVec(i) := ICacheValidBit(i)(AddrIndex) && ICacheTag(i).read(AddrIndex) === AddrTag
    //ヒットしたWayを記録
    when(hitVec(i))
    {
      hitWayNum = i
    }
  }

  val hit = hitVec.reduce(_ || _)

  //BRAMからRead
  val ReadData = ICacheDataBlock(hitWayNum).read(AddrIndex)

  val DataResponse = WireInit(UInt((params.ICacheBlockWidth / params.ICacheDataNum).W), 0.U)

  val ReturnFlag = RegInit(0.U(1.W))

  when(RegNext(hit))
  {
    /**
      * ヒットした場合
      */
    val DataHitOut = MuxLookup(AddrOffset, 0.U)(
        (0 until params.ICacheDataNum).map(i => i.U -> ReadData((params.ICacheBlockWidth / params.ICacheDataNum)*(i+1)-1, (params.ICacheBlockWidth / params.ICacheDataNum)*i))
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
    when(io.fetch.request.valid)
    {
      io.memory.request.valid := true.B
      val MemAddr = addr(63, 6)##0.U(6.W)
      io.memory.request.bits.address := MemAddr
      io.memory.request.bits.burstLength := 7.U
      when(RegNext(io.memory.request.ready))
      {
        io.memory.request.valid := false.B
      }
    }

    //データを受信
    val ReadDataBuf = RegInit(VecInit(Seq.fill(8)(0.U(64.W))))
    val count = RegInit(0.U(8.W))
    when(io.memory.request.ready) //<=何らかの条件必要
    {
      io.memory.response.ready := true.B
      
      //メモリからのデータ(64bit)をReadDataBufに格納
      when(io.memory.response.valid)
      {
        ReadDataBuf(count) := io.memory.response.bits.value
        count := count + 1.U
      }

      //ReadDataBufのデータ(64bit×8)をReadDataCom(512bit)に結合する
      when(count === 8.U)
      {
        when(io.memory.response.valid === false.B)
        {
          io.memory.response.ready := false.B
        }

        val ReadDataCom = Cat(ReadDataBuf(0), ReadDataBuf(1), ReadDataBuf(2), ReadDataBuf(3), ReadDataBuf(4), ReadDataBuf(5), ReadDataBuf(6), ReadDataBuf(7))
        Cat(ReadDataBuf.reverse)
        //DataResponseへ出力
        val DataMissOut = MuxLookup(AddrOffset, 0.U)(
            (0 until params.ICacheDataNum).map(i => i.U -> ReadDataCom((params.ICacheBlockWidth / params.ICacheDataNum)*((3-i)+1)-1, (params.ICacheBlockWidth / params.ICacheDataNum)*(3-i)))
        ) //<=修正必要
        DataResponse := DataMissOut
        ReturnFlag := 1.U

        /**
          * キャッシュへ書き込み
          * 有効ビット：true
          * タグ：AddrIndex
          * データブロック：ReadDataCom
          * ウェイの選択は"0"と"1"に順番に書き込む
          */
        //0||1を繰り返す
        
        val SelectWay = RegInit(1.U(1.W))
        when(count === 8.U)
        {
          SelectWay := SelectWay + 1.U
        }
        when(ReturnFlag === 1.U)
        {
          for((block, i) <- ICacheDataBlock.zipWithIndex) {
            when(SelectWay === i.U) {
              block.write(AddrIndex, ReadDataCom)
            }
          }
          for((tag, i) <- ICacheDataBlock.zipWithIndex) {
            when(SelectWay === i.U) {
              tag.write(AddrIndex, AddrTag)
            }
          }
          // ICacheDataBlock(SelectWay).write(AddrIndex, ReadDataCom)
          // ICacheTag(SelectWay).write(AddrIndex, AddrTag)
          ICacheValidBit(SelectWay)(AddrIndex) := true.B
        }
      }
    }
  }

  //命令をリターン
  when(ReturnFlag === 1.U)
  {
    io.fetch.response.valid := true.B
    io.fetch.response.bits := DataResponse
    when(io.fetch.response.ready)
    {
      io.fetch.response.valid := false.B
      // io.fetch.request.ready := true.B
      ReturnFlag := 0.U
    }
  }
*/
