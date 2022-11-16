package b4processor.connections

import b4processor.Parameters
import b4processor.structures.memoryAccess.MemoryAccessInfo
import b4processor.utils.Tag
import chisel3._

/** デコーダとLSQをつなぐ
  *
  * @param params
  *   パラメータ
  */
class Decoder2LoadStoreQueue(implicit params: Parameters) extends Bundle {

  /** メモリアクセスの情報 */
  val accessInfo = new MemoryAccessInfo()

  /** 命令自体を識別するためのタグ(Destination Tag) */
  val addressAndLoadResultTag = new Tag

  /** ストアに使用するデータが格納されるタグ(SourceRegister2 Tag) */
  val storeDataTag = new Tag

  /** ストアデータ */
  val storeData = UInt(64.W)

  /** ストアデータの値が有効か */
  val storeDataValid = Bool()
}
