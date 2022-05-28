package b4processor.modules.lsq

import b4processor.Parameters
import b4processor.utils.{DecodeEnqueue, LSQ2Memory, LSQfromALU}
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class LoadStoreQueueWrapper(implicit params: Parameters) extends LoadStoreQueue {

  def setMemoryReady(value: Boolean): Unit = {
    for(mem <- this.io.memory) {
      mem.ready.poke(value)
    }
  }

  def SetFromExecutor(values: Seq[Option[LSQfromALU]] = Seq.fill(params.numberOfALUs)(None)): Unit = {
    for(i <- 0 until params.numberOfALUs) {
      val alu = this.io.alus(i)
      val value = values(i)
      alu.valid.poke(value.get.valid)
      alu.value.poke(value.get.value)
      alu.destinationTag.poke(value.get.destinationtag)
      alu.ProgramCounter.poke(value.get.ProgramCounter)
    }
  }

  def SetDecoder(values: Seq[Option[DecodeEnqueue]] = Seq.fill(params.numberOfDecoders)(None)): Unit = {
    for(i <- 0 until params.numberOfDecoders) {
      val decode = this.io.decoders(i)
      val value = values(i)
      decode.valid.poke(value.get.valid)
      decode.bits.stag2.poke(value.get.stag2)
      decode.bits.value.poke(value.get.value)
      decode.bits.opcode.poke(value.get.opcode)
      decode.bits.programCounter.poke(value.get.ProgramCounter)
      decode.bits.function3.poke(value.get.function3)
    }
  }

  def SetReorderBuffer(ProgramCounters: Seq[Int], valids: Seq[Boolean]): Unit = {
    for(i <- 0 until params.maxRegisterFileCommitCount) {
      val pc = ProgramCounters(i)
      val v = valids(i)
      io.reorderbuffer.programCounter(i).poke(pc)
      io.reorderbuffer.valid(i).poke(v)
    }
  }

  def expectMemory(values: Seq[Option[LSQ2Memory]] = Seq.fill(params.maxLSQ2MemoryinstCount)(None)): Unit = {
    for(i <- 0 until params.maxLSQ2MemoryinstCount) {
      val memory = io.memory(i)
      val value = values(i)
      memory.valid.poke(value.isDefined)
      memory.bits.address.poke(value.get.address)
      memory.bits.tag.poke(value.get.tag)
      memory.bits.opcode.poke(value.get.opcode)
      memory.bits.function3.poke(value.get.function3)
      memory.bits.data.poke(value.get.data)
    }
  }
}

class LoadStoreQueueTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Load Store Queue"
  implicit val defalutParams = Parameters(tagWidth = 4, numberOfDecoders = 1, maxRegisterFileCommitCount = 2, maxLSQ2MemoryinstCount = 2)

  it should "enpqueue to LSQ" in {
    test(new LoadStoreQueueWrapper) { c =>
      c.SetDecoder(values = )
      c.setMemoryReady(true)
    }
  }

}
