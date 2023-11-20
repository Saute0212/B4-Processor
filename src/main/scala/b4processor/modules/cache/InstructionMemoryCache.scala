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

  io.fetch.request.ready := DontCare
  io.fetch.response.bits := DontCare
  io.fetch.response.valid := DontCare

  io.memory.request.valid := DontCare
  io.memory.request.bits := DontCare
  io.memory.response.ready := DontCare
}

object InstructionMemoryCache extends App {
  implicit val params = Parameters()
  ChiselStage.emitSystemVerilogFile(new InstructionMemoryCache)
}
