sealed class OrderDirection(direction: Boolean)
{
  def apply()=direction
  override def toString: String = if (direction) "ASCending" else "DESCending"
}
object OrderDirection {
  def apply(direction: Boolean) = new OrderDirection(direction)
  implicit def toBoolean(direction: OrderDirection) = direction.apply()
  case object ASC extends OrderDirection(true)
  case object DESC extends OrderDirection(false)
}

sealed class UniqueElements(unique: Boolean = false)
{
  def apply()=unique
  override def toString: String = if (unique) "Unique" else "Can repeating"
}
object UniqueElements {
  def apply(unique: Boolean) = new UniqueElements(unique)
  implicit def toBoolean(unique: UniqueElements) = unique.apply()
  case object Unique extends UniqueElements(true)
  case object Repeatable extends UniqueElements(false)
}

case class StreamOrder[K](direction: OrderDirection = OrderDirection.ASC,
                          unique: UniqueElements = new UniqueElements)
                         (implicit ordering: Ordering[K])
{
  import OrderDirection._
  def comp[X](keymaker: X=>K) = direction match {
    case ASC  => (x:X,y:X)=>  ordering.compare(keymaker(x),keymaker(y))
    case DESC => (x:X,y:X)=>  ordering.reverse.compare(keymaker(x),keymaker(y))
  }
}



