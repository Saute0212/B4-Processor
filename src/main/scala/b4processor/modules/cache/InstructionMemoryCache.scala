package b4processor.modules.cache

import b4processor.Parameters
import b4processor.modules.memory.MemoryReadChannel
import circt.stage.ChiselStage
import chisel3._
import chisel3.util._
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
  io.memory.request.bits.address := 0.U
  io.memory.response.ready := false.B

  io.memory.request.bits.size := MemoryAccessWidth.DoubleWord
  io.memory.request.bits.burstLength := (params.MemoryBurstLength - 1).U

  // アドレスを格納
  val addr = WireInit(UInt(64.W), 0.U)
  addr := io.fetch.request.bits

  // タグ・インデックス・オフセットの抽出
  val IgnoreBits = log2Up(16)
  val OffsetBits = log2Up(params.ICacheDataNum)
  val IndexBits = log2Up(params.ICacheSet)
  val TagBits = 64 - IndexBits - OffsetBits - IgnoreBits
  val AddrOffset = addr(IgnoreBits + OffsetBits - 1, IgnoreBits)
  val AddrOffsetReg = RegInit(0.U(OffsetBits.W))
  val AddrIndex = RegPassthrough(
    io.fetch.request.valid,
    addr(IgnoreBits + OffsetBits + IndexBits - 1, IgnoreBits + OffsetBits),
    0.U,
  )
  val AddrIndexReg = RegInit(0.U(IndexBits.W))
  val AddrTag = addr(63, IgnoreBits + OffsetBits + IndexBits)
  val AddrTagReg = RegInit(0.U(TagBits.W))
  val AddrRequest = addr(63, 6) ## 0.U(6.W)
  val AddrRequestReg = RegInit(0.U(64.W))

  // 有効ビット・タグ・インデックス
  val ICacheValidBit = RegInit(
    VecInit(
      Seq.fill(params.ICacheWay)(VecInit(Seq.fill(params.ICacheSet)(false.B))),
    ),
  )
  val ICacheTag =
    Seq.fill(params.ICacheWay)(SyncReadMem(params.ICacheSet, UInt(TagBits.W)))
  val ICacheDataBlock = Seq.fill(params.ICacheWay)(
    SyncReadMem(params.ICacheSet, UInt(params.ICacheBlockWidth.W)),
  )

  // バースト転送のバッファー
  val ReadDataBuf = RegInit(
    VecInit(Seq.fill(params.MemoryBurstLength)(0.U(64.W))),
  )

  // バースト転送のカウンター
  val count = RegInit(0.U(8.W))

  // ウェイのカウンター(各セットごとにカウンターを用意)
  val SelectWay = RegInit(VecInit(Seq.fill(params.ICacheSet)(0.U(1.W))))

  when(io.fetch.request.valid) {
    AddrOffsetReg := AddrOffset
    AddrIndexReg := AddrIndex
    AddrTagReg := AddrTag
    AddrRequestReg := AddrRequest
  }
  io.fetch.request.ready := RegNext(io.fetch.request.valid)

  // ヒットするか判定
  val hitVec = WireInit(VecInit(Seq.fill(params.ICacheWay)(false.B)))
  val hitVecReg = RegInit(VecInit(Seq.fill(params.ICacheWay)(false.B)))
  val hitWayNum = WireInit(0.U(log2Up(params.ICacheWay).W))
  for (i <- 0 until params.ICacheWay) {
    hitVec(i) := ICacheValidBit(i)(AddrIndexReg) &&
      ICacheTag(i).read(AddrIndex) === AddrTagReg
    hitVecReg(i) := hitVec(i)
  }
  hitWayNum := MuxCase(0.U, hitVec.zipWithIndex.map { case (h, i) => h -> i.U })

  // BRAMからRead
  val ReadData = MuxLookup(hitWayNum, 0.U)(
    (0 until params.ICacheWay).map(i =>
      i.U -> ICacheDataBlock(i).read(AddrIndex),
    ),
  )

  val hit = hitVec.reduce(_ || _)

  private val idle :: requesting :: waitingResponse :: writeReadDataBuf :: Nil =
    Enum(4)
  private val memory_state = RegInit(idle)

  when(memory_state === idle) {
    // nothing
  }.elsewhen(memory_state === requesting) {
    io.memory.request.valid := true.B
    io.memory.request.bits.address := AddrRequestReg
    when(io.memory.request.ready) {
      memory_state := waitingResponse
    }
  }.elsewhen(memory_state === waitingResponse) {
    io.memory.response.ready := true.B
    when(io.memory.response.valid) {
      // メモリからのデータ(64bit)をReadDataBufに格納
      ReadDataBuf(io.memory.response.bits.burstIndex) :=
        io.memory.response.bits.value

      when(io.memory.response.bits.burstIndex === 7.U) {
        memory_state := writeReadDataBuf
      }
    }
  }.elsewhen(memory_state === writeReadDataBuf) {
    val ReadDataCom = Cat(ReadDataBuf.reverse)

    ICacheDataBlock(1).write(AddrIndexReg, ReadDataCom)
    ICacheTag(1).write(AddrIndexReg, AddrTagReg)
    ICacheValidBit(1)(AddrIndexReg) := true.B

    memory_state := idle
  }.otherwise {
    assert(false.B)
  }

  when(hit) {
    // ヒットした場合
    val DataHitOut = MuxLookup(AddrOffsetReg, 0.U)(
      (0 until params.ICacheDataNum).map(i =>
        i.U -> ReadData(
          (params.ICacheBlockWidth / params.ICacheDataNum) * (i + 1) - 1,
          (params.ICacheBlockWidth / params.ICacheDataNum) * i,
        ),
      ),
    )

    io.fetch.response.valid := true.B
    io.fetch.response.bits := DataHitOut
  }.otherwise {
    // ミスした場合
    when(RegNext(io.fetch.request.valid) && memory_state === idle) {
      memory_state := requesting
    }
  }
}

object RegPassthrough {
  def apply[T <: Data](valid: Bool, value: T, init: T): T = {
    val r = RegInit(init)
    val w = WireDefault(r)
    when(valid) {
      w := value
      r := value
    }
    w
  }
}

object InstructionMemoryCache extends App {
  implicit val params = Parameters()
  ChiselStage.emitSystemVerilogFile(new InstructionMemoryCache)
}

/*
  === Test Result : 2023/12/29 01:20 ===
  Total Test : 31
  Succeeded  : 29
  Failed     : 2
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
