package b4processor.modules.reservationstation

import b4processor.Parameters
import b4processor.connections.{Decoder2ReservationStation, ExecutionRegisterBypass, ReservationStation2Executor}
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

class ReservationStation(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val bypassValues = Flipped(Vec(params.runParallel, new ExecutionRegisterBypass))
    val executor = new ReservationStation2Executor
    val decoder = Flipped(new Decoder2ReservationStation)
  })

  val reservation = RegInit(VecInit(Seq.fill(math.pow(2, params.tagWidth).toInt / params.runParallel)(ReservationStationEntry.default)))
  val emptyList = reservation.map { r => !r.valid }
  val readyList = reservation.map { r => r.ready1 && r.ready2 }

  val hasEmpty = Cat(emptyList).orR
  val emptyIndex = MuxCase(0.U, emptyList.zipWithIndex.map { case (empty, index) => empty -> index.U })
  val hasReady = Cat(readyList).orR
  val executeIndex = MuxCase(0.U, readyList.zipWithIndex.map { case (ready, index) => ready -> index.U })

  //  printf(p"hasEmpty=$hasEmpty at $emptyIndex hasReady=$hasReady at $executeIndex\n")
  //  printf(p"reserved0 valid=${reservation(0).valid} ready1=${reservation(0).ready1} value1=${reservation(0).value1}\n")

  // 実行ユニットへ
  when(io.executor.ready && hasReady) {
    reservation(executeIndex) := 0.U.asTypeOf(new ReservationStationEntry)
    //      printf(p"from reserved $executeIndex\n")
  }.otherwise {
    //      printf("no output\n")
  }
  io.executor.valid := hasReady
  io.executor.bits.opcode := reservation(executeIndex).opcode
  io.executor.bits.destinationTag := reservation(executeIndex).destinationTag
  io.executor.bits.value1 := reservation(executeIndex).value1
  io.executor.bits.value2 := reservation(executeIndex).value2
  io.executor.bits.programCounter := reservation(executeIndex).programCounter
  io.executor.bits.function3 := reservation(executeIndex).function3
  io.executor.bits.immediateOrFunction7 := reservation(executeIndex).immediateOrFunction7

  // デコーダから
  io.decoder.ready := hasEmpty
  when(io.decoder.entry.valid && hasEmpty) {
    reservation(emptyIndex) := io.decoder.entry
    //    printf(p"stored in $emptyIndex valid=${io.decoder.entry.valid}\n")
  }

  for (value <- io.bypassValues) {
    when(value.valid) {
      for (entry <- reservation) {
        when(entry.valid) {
          when(!entry.ready1 && entry.sourceTag1 === value.destinationTag) {
            entry.value1 := value.value
            entry.ready1 := true.B
          }
          when(!entry.ready2 && entry.sourceTag2 === value.destinationTag) {
            entry.value2 := value.value
            entry.ready2 := true.B
          }
        }
      }
    }
  }
}

object ReservationStation extends App {
  implicit val params = Parameters(tagWidth = 2, runParallel = 1)
  (new ChiselStage).emitVerilog(new ReservationStation(), args = Array("--emission-options=disableMemRandomization,disableRegisterRandomization"))
}