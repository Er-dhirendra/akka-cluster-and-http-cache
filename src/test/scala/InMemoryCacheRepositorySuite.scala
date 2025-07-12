import com.repository.InMemoryCacheRepository
import munit.FunSuite

class InMemoryCacheRepositorySuite extends FunSuite {

  test("put and get a value") {
    val repo = new InMemoryCacheRepository
    repo.put("foo", "bar")
    assertEquals(repo.get("foo"), Some("bar"))
  }

  test("get returns None for missing key") {
    val repo = new InMemoryCacheRepository
    assertEquals(repo.get("missing"), None)
  }

  test("delete returns true for existing key and removes it") {
    val repo = new InMemoryCacheRepository
    repo.put("deleteKey", "value")
    val deleted = repo.delete("deleteKey")
    val postDelete = repo.get("deleteKey")

    assertEquals(deleted, true)
    assertEquals(postDelete, None)
  }

  test("delete returns false for non-existing key") {
    val repo = new InMemoryCacheRepository
    val deleted = repo.delete("nonExistentKey")
    assertEquals(deleted, false)
  }
}
