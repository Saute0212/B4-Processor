//package b4processor.modules.cache
//
//import b4processor.Parameters
//import chisel3._
//import chiseltest._
//import org.scalatest.flatspec.AnyFlatSpec
//
///** メモリをキャッシュを含んだラッパー */
//class InstructionMEmoryCacheWrapper()(implicit params: Parameters)
//    extends InstructionMemoryCache {
//  def initialize() = {}
//
//  def responseStep(data: UInt) = {
//    this.io.memory.response.valid.poke(true)
//    this.io.memory.response.bits.value.poke(data)
//    this.clock.step()
//    this.io.memory.response.valid.poke(false)
//    this.io.memory.response.bits.value.poke(0)
//  }
//
//  def setFetch(address: UInt) = {
//    this.io.fetch(0).address.valid.poke(true)
//    this.io.fetch(0).address.bits.poke(address)
//  }
//
//  def expectRequest(address: UInt) = {
//    this.io.memory.request.valid.expect(true)
//    this.io.memory.request.bits.address.expect(address)
//    this.io.memory.request.bits.burstLength.expect(1)
//  }
//
//  def expectData(data: UInt) = {
//    this.io.fetch(0).output.valid.expect(true)
//    this.io.fetch(0).output.bits.expect(data)
//  }
//}
//
//class InstructionMemoryCacheTest
//    extends AnyFlatSpec
//    with ChiselScalatestTester {
//  behavior of "Instruction Cache"
//  implicit val defaultParams = Parameters(fetchWidth = 2)
//
//  it should "load memory" in {
//    test(new InstructionMEmoryCacheWrapper)
//      .withAnnotations(Seq(WriteVcdAnnotation)) { c =>
//        c.setFetch("x2222222200000000".U)
//        c.clock.step()
//        c.expectRequest("x2222222200000000".U)
//        c.io.memory.request.ready.poke(true)
//        c.clock.step(2)
//        c.responseStep("x2020202010101010".U)
//        c.clock.step(2)
//        c.responseStep("x4040404030303030".U)
//        c.clock.step(2)
//
//        c.expectData("x10101010".U)
//        c.clock.step()
//
//        c.setFetch("x2222222200000004".U)
//        c.clock.step()
//        c.expectData("x20202020".U)
//      }
//  }
//}
