package b4processor.modules.decoder

import b4processor.Parameters
import b4processor.connections._
import b4processor.utils.{FormalTools, Tag}
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import b4processor.modules.Decoder2AtomicLSU
import b4processor.modules.reservationstation.ReservationStationEntry
import b4processor.utils.operations.{
  ALUOperation,
  AMOOperation,
  CSROperation,
  DecodingMod,
  LoadStoreOperation
}

/** デコーダ
  */
class Decoder(implicit params: Parameters) extends Module with FormalTools {
  val io = IO(new Bundle {
    val threadId = Input(UInt(log2Up(params.threads).W))

    val instructionFetch = Flipped(new Uncompresser2Decoder())
    val reorderBuffer = new Decoder2ReorderBuffer
    val outputCollector = Flipped(new CollectedOutput())
    val registerFile = new Decoder2RegisterFile()

    val reservationStation = new Decoder2ReservationStation
    val loadStoreQueue = Decoupled(new Decoder2LoadStoreQueue)
    val csr = Decoupled(new Decoder2CSRReservationStation())
    val amo = Decoupled(new Decoder2AtomicLSU)
  })

  val operations = DecodingMod(
    io.instructionFetch.bits.instruction,
    io.instructionFetch.bits.programCounter
  )

  io.reorderBuffer.isDecodeError := io.instructionFetch.valid && !operations.valid

  val operationInorder =
    LoadStoreOperation.Store === operations.loadStoreOp.validDataOrZero ||
      operations.amoOp.isValid ||
      operations.csrOp.isValid

  // リオーダバッファへの入力
  io.reorderBuffer.sources zip operations.sources foreach { case (rob, o) =>
    rob.sourceRegister := o.reg
  }
  io.reorderBuffer.destination.destinationRegister := operations.rd
  io.reorderBuffer.destination.operationInorder := operationInorder
  io.reorderBuffer.programCounter := io.instructionFetch.bits.programCounter

  // レジスタファイルへの入力
  io.registerFile.sourceRegisters zip operations.sources foreach {
    case (rf, o) => rf := o.reg
  }

  // リオーダバッファから一致するタグを取得する
  val sourceTags = Wire(Vec(3, Valid(new Tag)))
  sourceTags := io.reorderBuffer.sources.map(_.matchingTag)
  if (!params.enablePExt) {
    sourceTags(2).valid := false.B
    sourceTags(2).bits := Tag(io.threadId, 0.U)
  }
  val destinationTag = io.reorderBuffer.destination.destinationTag

  // Valueの選択
  val values = Wire(Vec(3, Valid(UInt(64.W))))
  values := io.reorderBuffer.sources zip io.registerFile.values zip sourceTags map {
    case ((rob, rf), st) =>
      ValueSelector.getValue(rob.value, rf, io.outputCollector, st)
  }
  if (!params.enablePExt)
    values(2) := 0.U.asTypeOf(Valid(UInt(64.W)))

  // 命令をデコードするのはリオーダバッファにエントリの空きがあり、リザベーションステーションにも空きがあるとき
  io.instructionFetch.ready := io.reservationStation.ready && io.reorderBuffer.ready && io.loadStoreQueue.ready && io.csr.ready && io.amo.ready
  // リオーダバッファやリザベーションステーションに新しいエントリを追加するのは命令がある時
  io.reorderBuffer.valid := io.instructionFetch.ready &&
    io.instructionFetch.valid

  // RSへの出力を埋める
  val rs = io.reservationStation.entry
  rs := 0.U.asTypeOf(new ReservationStationEntry)
  rs.valid := io.instructionFetch.ready &&
    io.instructionFetch.valid && operations.aluOp.isValid
  rs.sources foreach { s => s.tag.threadId := io.threadId }
  when(rs.valid) {
    rs.operation := operations.aluOp.validDataOrZero
    rs.destinationTag := destinationTag
    rs.sources zip values zip sourceTags zip operations.sources foreach {
      case (((rs_s, v), st), o) => {
        rs_s.ready := v.valid || o.valid
        rs_s.value := MuxCase(0.U, Seq(o.valid -> o.value, v.valid -> v.bits))
        rs_s.tag := Mux(v.valid || o.valid, Tag(io.threadId, 0.U), st.bits)
      }
    }

    rs.branchOffset := operations.branchOffset
    rs.wasCompressed := io.instructionFetch.bits.wasCompressed
  }

  // load or store命令の場合，LSQへ発送
  io.loadStoreQueue.bits := 0.U.asTypeOf(new Decoder2LoadStoreQueue)
  io.loadStoreQueue.valid := io.loadStoreQueue.ready && io.instructionFetch.ready && io.instructionFetch.valid && operations.loadStoreOp.isValid
  io.loadStoreQueue.bits.destinationTag.threadId := io.threadId
  io.loadStoreQueue.bits.storeDataTag.threadId := io.threadId
  io.loadStoreQueue.bits.addressTag.threadId := io.threadId
  when(io.loadStoreQueue.valid) {
    io.loadStoreQueue.bits.operation := operations.loadStoreOp.validDataOrZero
    io.loadStoreQueue.bits.operationWidth := operations.loadStoreWidth
    io.loadStoreQueue.bits.destinationTag := destinationTag
    io.loadStoreQueue.bits.addressValid := values(0).valid
    io.loadStoreQueue.bits.address := values(0).bits
    io.loadStoreQueue.bits.addressTag := sourceTags(0).bits
    io.loadStoreQueue.bits.addressOffset := operations
      .sources(1)
      .value(11, 0)
      .asSInt
    when(operationInorder) {
      io.loadStoreQueue.bits.storeDataTag := sourceTags(1).bits
      io.loadStoreQueue.bits.storeData := values(1).bits
      io.loadStoreQueue.bits.storeDataValid := values(1).valid
    }.otherwise {
      io.loadStoreQueue.bits.storeDataTag := Tag(io.threadId, 0.U)
      io.loadStoreQueue.bits.storeData := 0.U
      io.loadStoreQueue.bits.storeDataValid := true.B
    }
  }

  io.csr.valid := io.csr.ready && io.instructionFetch.ready && io.instructionFetch.valid & operations.csrOp.isValid
  io.csr.bits := 0.U.asTypeOf(new Decoder2CSRReservationStation)
  io.csr.bits.sourceTag.threadId := io.threadId
  io.csr.bits.destinationTag.threadId := io.threadId
  when(io.csr.valid) {
    io.csr.bits.operation := operations.csrOp.validDataOrZero
    io.csr.bits.address := operations.sources(1).value
//    printf(p"decoder out ${operations.csrOp}\n")
    io.csr.bits.sourceTag := sourceTags(0).bits
    io.csr.bits.value := MuxCase(
      0.U,
      Seq(
        operations.sources(0).valid -> operations.sources(0).value,
        values(0).valid -> values(0).bits
      )
    )
    io.csr.bits.ready := operations.sources(0).valid || values(0).valid
    io.csr.bits.destinationTag := io.reorderBuffer.destination.destinationTag
  }

  io.amo.valid := io.amo.ready && io.instructionFetch.ready && io.instructionFetch.valid && operations.amoOp.isValid
  io.amo.bits := 0.U.asTypeOf(new Decoder2AtomicLSU)
  io.amo.bits.srcReg.threadId := io.threadId
  io.amo.bits.destinationTag.threadId := io.threadId
  when(io.amo.valid) {
    io.amo.bits.valid := true.B
    io.amo.bits.operation := operations.amoOp.validDataOrZero
    io.amo.bits.operationWidth := operations.amoWidth
    io.amo.bits.ordering := operations.amoOrdering

    io.amo.bits.addressReg := sourceTags(0).bits
    io.amo.bits.addressValue := values(0).bits
    io.amo.bits.addressValid := values(0).valid

    io.amo.bits.srcReg := sourceTags(1).bits
    io.amo.bits.srcValue := values(1).bits
    io.amo.bits.srcValid := values(1).valid

    io.amo.bits.destinationTag := destinationTag
  }

  // FORMAL
  takesEveryValue(io.reorderBuffer.valid)
  takesEveryValue(io.reservationStation.entry.valid)
  takesEveryValue(io.loadStoreQueue.valid)
  takesEveryValue(io.csr.valid)
  takesEveryValue(io.amo.valid)

  // source and destination tags threadid should be const
  rs.sources.zipWithIndex foreach { case (s, i) =>
    assert(s.tag.threadId === io.threadId, s"thread id wrong on source(${i})")
  }
  assert(
    io.amo.bits.srcReg.threadId === io.threadId,
    "amo source thread id wrong"
  )
  assert(
    io.amo.bits.destinationTag.threadId === io.threadId,
    "amo destination thread id wrong"
  )
  assert(io.csr.bits.sourceTag.threadId === io.threadId)
  assert(io.csr.bits.destinationTag.threadId === io.threadId)
  assert(io.loadStoreQueue.bits.destinationTag.threadId === io.threadId)
  assert(io.loadStoreQueue.bits.storeDataTag.threadId === io.threadId)
  assert(io.loadStoreQueue.bits.addressTag.threadId === io.threadId)

  // assumptions
  assume(stable(io.threadId))
  io.reorderBuffer.sources.zipWithIndex foreach { case (s, i) =>
    assume(
      s.matchingTag.bits.threadId === io.threadId,
      s"reorder buffer source $i threadid should be the same as decoder"
    )
  }
  assume(
    io.reorderBuffer.destination.destinationTag.threadId === io.threadId,
    "reorder buffer destination threadid should be the same as decoder"
  )
}

object Decoder extends App {
  implicit val params = Parameters(enablePExt = false)
  ChiselStage.emitSystemVerilogFile(new Decoder)
}
