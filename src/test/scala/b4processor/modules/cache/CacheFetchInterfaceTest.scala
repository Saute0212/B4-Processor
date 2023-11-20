package b4processor.modules.cache

import b4processor.Parameters
import org.scalatest.flatspec.AnyFlatSpec
import utest.test
import chiseltest._
import chisel3._
import chisel3.util._

class CacheFetchInterfaceTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "cache fetch interface"
  implicit val params = Parameters()

  it should "request address" in {
    test(new CacheFetchInterface())
      .withAnnotations(Seq(IcarusBackendAnnotation, WriteVcdAnnotation)) { c =>
        c.io.fetch.requestNext.bits.poke(0x80000000L)
        c.io.fetch.requestNext.valid.poke(true)
        c.io.fetch.perDecoder.zipWithIndex foreach { case (f, i) =>
          f.request.valid.poke(true)
          f.request.bits.poke(0x80000000L + (i * 4))
        }
        c.clock.step()
        c.io.cache.request.ready.poke(true)

        c.clock.step(5)

        c.io.cache.response.valid.poke(true)
        c.io.cache.response.bits.poke("x01234567_89ABCDEF_01234567_89ABCDEF".U)

        c.io.fetch.perDecoder foreach { f =>
          f.response.valid.expect(true)
        }

        c.clock.step(5)
      }
  }
}
