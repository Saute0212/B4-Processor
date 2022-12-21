package b4processor.Z_B4ProcessorTests

import b4processor.Parameters
import b4processor.utils.B4ProcessorWithMemory
import chiseltest._
import chiseltest.internal.CachingAnnotation
import org.scalatest.flatspec.AnyFlatSpec

class B4ProcessorProgramTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "B4Processor"
  // デバッグに時間がかかりすぎるのでパラメータを少し下げる。
  implicit val defaultParams =
    Parameters(debug = true, tagWidth = 4, threads = 1, decoderPerThread = 1)

  // branchプログラムが実行できる
  it should "execute branch with no parallel" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(threads = 1, decoderPerThread = 1)
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/branch/branch")
        c.checkForRegister(13, 20, 40)
      }
  }
  // branchプログラムが実行できる
  it should "execute branch with no parallel 2 thread" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(threads = 2, decoderPerThread = 1)
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/branch/branch")
        c.checkForRegister(13, 20, 40, 0)
        c.checkForRegister(13, 20, 40, 1)
      }
  }

  // フィボナッチ数列の計算が同時発行数1でできる
  it should "execute fibonacci with no parallel" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(threads = 1, decoderPerThread = 1)
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/fibonacci/fibonacci")
        c.checkForRegister(6, 55, 200)
      }
  }

  // フィボナッチ数列の計算が同時発行数2でできる
  it should "execute fibonacci with 2 parallel" in {
    test(new B4ProcessorWithMemory())
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/fibonacci/fibonacci")
        c.checkForRegister(6, 55, 200)
      }
  }

  // フィボナッチ数列の計算が同時発行数4でできる
  it should "execute fibonacci with 4 parallel" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(threads = 1, decoderPerThread = 4)
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/fibonacci/fibonacci")
        c.checkForRegister(6, 55, 200)
      }
  }

  // call(JALRを使った関数呼び出し)とret(JALRを使った関数からのリターン)がうまく実行できる
  it should "execute call_ret with 2 parallel" in {
    test(new B4ProcessorWithMemory())
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/call_ret/call_ret")
        c.checkForRegister(5, 1, 20)
        c.checkForRegister(6, 2, 20)
        c.checkForRegister(7, 3, 20)
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数1で試す
  it should "execute many_add with no parallel" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(threads = 1, decoderPerThread = 1)
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/many_add/many_add")
        c.checkForRegister(1, 8, 70)
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数1で試す
  it should "execute many_add with no parallel 2 thread" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(threads = 2, decoderPerThread = 1)
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/many_add/many_add")
        c.checkForRegister(1, 8, 70, 0)
        c.checkForRegister(1, 8, 70, 1)
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数2で試す
  it should "execute many_add with 2 parallel" in {
    test(new B4ProcessorWithMemory()(defaultParams.copy(fetchWidth = 8)))
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/many_add/many_add")
        c.checkForRegister(1, 8, 70)
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数4で試す
  it should "execute many_add with 4 parallel" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(threads = 1, decoderPerThread = 4, fetchWidth = 8)
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/many_add/many_add")
        c.checkForRegister(1, 8, 70)
      }
  }

  // タグ幅をとても小さくする（すべてのデコーダが使えない）ような状況でもうまく動作する
  it should "execute many_add with 4 parallel with very low tag width" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams
          .copy(threads = 1, decoderPerThread = 4, fetchWidth = 8, tagWidth = 2)
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/many_add/many_add")
        c.checkForRegister(1, 8, 70)
      }
  }

  // 並列実行できそうな大量のadd命令を同時発行数8で試す
  it should "execute many_add with 8 parallel" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(
          threads = 1,
          decoderPerThread = 8,
          fetchWidth = 8,
          maxRegisterFileCommitCount = 10
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/many_add/many_add")
        c.checkForRegister(1, 8, 70)
      }
  }

  // アウトオブオーダでできそうな命令を同時発行数4で試す
  it should "execute out_of_order with 4 parallel" in {
    test(
      new B4ProcessorWithMemory(
      )(
        defaultParams
          .copy(
            threads = 1,
            decoderPerThread = 4,
            fetchWidth = 8,
            maxRegisterFileCommitCount = 8
          )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize(
          "riscv-sample-programs/many_add_out_of_order/many_add_out_of_order"
        )
        c.clock.step(30)
        c.io.registerFileContents.get(0)(1).expect(1)
        c.io.registerFileContents.get(0)(2).expect(2)
        c.io.registerFileContents.get(0)(3).expect(3)
        c.io.registerFileContents.get(0)(4).expect(4)
        c.io.registerFileContents.get(0)(5).expect(1)
        c.io.registerFileContents.get(0)(6).expect(2)
        c.io.registerFileContents.get(0)(7).expect(3)
        c.io.registerFileContents.get(0)(8).expect(4)
        c.io.registerFileContents.get(0)(9).expect(1)
        c.io.registerFileContents.get(0)(10).expect(2)
        c.io.registerFileContents.get(0)(11).expect(3)
        c.io.registerFileContents.get(0)(12).expect(4)
      }
  }

  // 単純な値をストアしてロードするプログラム
  it should "run load_store" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(
          threads = 1,
          decoderPerThread = 1,
          maxRegisterFileCommitCount = 1
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c => }
  }

  // 単純な値をストアしてロードするプログラム同時発行数2
  it should "run load_store with 2 parallel" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(threads = 1, decoderPerThread = 2)
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/load_store/load_store")
        c.checkForRegister(3, 10, 120)
        c.io.registerFileContents
          .get(0)(1)
          .expect(defaultParams.instructionStart + 0x18)
        c.io.registerFileContents.get(0)(2).expect(10)
        c.io.registerFileContents.get(0)(3).expect(10)
      }
  }

  it should "run fibonacci_c" in {
    test(
      new B4ProcessorWithMemory(
      )(
        defaultParams.copy(
          threads = 1,
          decoderPerThread = 1,
          maxRegisterFileCommitCount = 1,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/fibonacci_c/fibonacci_c")
        c.checkForRegister(3, 21, 1500)
      }
  }

  it should "run fibonacci_c with 2 parallel" in {
    test(
      new B4ProcessorWithMemory(
      )(
        defaultParams.copy(
          threads = 1,
          decoderPerThread = 2,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 3
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/fibonacci_c/fibonacci_c")
        c.checkForRegister(3, 21, 1000)
      }
  }

  it should "run load_plus_arithmetic" in {
    test(
      new B4ProcessorWithMemory(
      )(
        defaultParams.copy(
          threads = 1,
          decoderPerThread = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize(
          "riscv-sample-programs/load_plus_arithmetic/load_plus_arithmetic"
        )
        c.checkForRegister(2, 20, 50)
        c.checkForRegister(3, 1, 50)
      }
  }

  it should "run load_after_store" in {
    test(
      new B4ProcessorWithMemory(
      )(
        defaultParams.copy(
          threads = 1,
          decoderPerThread = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/load_after_store/load_after_store")
        c.checkForRegister(3, 10, 100)
      }
  }

  it should "run enter_c" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(
          threads = 1,
          decoderPerThread = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/enter_c/enter_c")
        c.checkForRegister(3, 5, 100)

      }
  }

  it should "run calculation_c" in {
    test(
      new B4ProcessorWithMemory(
      )(
        defaultParams.copy(
          threads = 1,
          decoderPerThread = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/calculation_c/calculation_c")
        c.checkForRegister(3, 18, 400)

      }
  }

  it should "run loop_c" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(
          threads = 1,
          decoderPerThread = 1,
          maxRegisterFileCommitCount = 1,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/loop_c/loop_c")
        c.checkForRegister(3, 30, 500)

      }
  }

  it should "run loop_c with 4 parallel" in {
    test(
      new B4ProcessorWithMemory()(
        defaultParams.copy(
          threads = 1,
          decoderPerThread = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/loop_c/loop_c")
        c.checkForRegister(3, 30, 500)

      }
  }

  it should "run many_load_store" in {
    test(
      new B4ProcessorWithMemory(
      )(
        defaultParams.copy(
          threads = 1,
          decoderPerThread = 1,
          maxRegisterFileCommitCount = 1,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/many_load_store/many_load_store")
        c.checkForRegister(2, 36, 500)

      }
  }

  it should "run many_load_store with 4 parallel" in {
    test(
      new B4ProcessorWithMemory(
      )(
        defaultParams.copy(
          threads = 1,
          decoderPerThread = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/many_load_store/many_load_store")
        c.checkForRegister(2, 36, 100)

      }
  }

  it should "run load_store_cross" in {
    test(
      new B4ProcessorWithMemory(
      )(
        defaultParams.copy(
          threads = 1,
          decoderPerThread = 1,
          maxRegisterFileCommitCount = 1,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/load_store_cross/load_store_cross")
        c.checkForRegister(2, 101, 100)
        c.checkForRegister(3, 201, 100)

      }
  }

  it should "run load_store_cross with 4 parallel" in {
    test(
      new B4ProcessorWithMemory(
      )(
        defaultParams.copy(
          threads = 1,
          decoderPerThread = 4,
          maxRegisterFileCommitCount = 4,
          loadStoreQueueIndexWidth = 2
        )
      )
    )
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, CachingAnnotation)
      ) { c =>
        c.initialize("riscv-sample-programs/load_store_cross/load_store_cross")
        c.checkForRegister(2, 101, 100)
        c.checkForRegister(3, 201, 100)
      }
  }
}
