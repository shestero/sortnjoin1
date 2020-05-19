import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.collection.{immutable, mutable}
import scala.concurrent.{ExecutionContext, Future}

import DoAfter._

object StreamSort {
  def apply[E,K]()(implicit keymaker: E=>K, order: StreamOrder[K]) = new StreamSort[E,K](keymaker,order)
}

class StreamSort[E,K](keymaker: E=>K, order: StreamOrder[K])
{
  implicit def ordering: Ordering[E] = new Ordering[E] {
    override def compare(x: E, y: E): Int = order.comp(keymaker)(x, y)
  }

  def fullSort(bufSize: Int = Int.MaxValue)(implicit mat: Materializer, ec: ExecutionContext): Flow[E, E, Future[Int]] = {
    val (fseq, inp) = Flow[E].take(bufSize).toMat(Sink.seq)(Keep.right).preMaterialize()
    val sorted = fseq.map(_.sorted(ordering))
    Flow.fromSinkAndSource(inp, Source.future(sorted).mapConcat(identity))
      .mapMaterializedValue(_ => sorted.map(_.size)) // - to return Future of length
  } //.assert(keymaker)

  // TODO: test
  def partSort(bufSize: Int): Flow[E, (E,Long), _] = partSort((_,_)=>false,bufSize)
  def partSort(distance: (E,E)=>Boolean, bufSize: Int = Int.MaxValue): Flow[E, (E,Long), _] = {
    val ss = mutable.SortedSet.empty[(E,Long)]
    Flow[E].zipWithIndex.statefulMapConcat { () =>
      e: (E, Long) =>
        val ee = e._1
        ss += e
        //println(s"ee=$ee; ss: " + ss.mkString(","))
        def distanceExt(x: (E, Long)) = distance(x._1, ee)
        val overflow = ss.takeWhile(distanceExt)
        //println("overflow: " + overflow.mkString(","))

        ss --= overflow
        val it = new Iterator[(E, Long)] {
          override def hasNext: Boolean = ss.size > bufSize
          override def next(): (E, Long) = doAfter(() => ss.head, ss.remove _)
        }
        immutable.List.from(overflow ++ it).toIterable
    } ++ Source.lazySource(()=>Source.fromIterator(()=>ss.iterator))
  } //.via( assert(keymaker) )

  // TODO: calculate a minimal distance and/or minimal frame buffer size to sort the given stream

  def checkBool(): Flow[E, (Boolean,E), _] = {
    var last: Option[E] = None
    Flow[E].map { e => // mapAsync(1)
      doAfter( ()=>
        last.forall(le => order.comp(keymaker)(le, e) match {
          case 0 => !order.unique
          case i => i < 0
        }) -> e
      , ()=> last=Some(e) )
    }
  }

  object UniqueViolation extends Exception
  object OrderViolation extends Exception
  def checkException(): Flow[E, (Option[Exception],E), _] = {
    var last: Option[E] = None
    Flow[E].map { e => // mapAsync(1)
      doAfter( () =>
        last.flatMap(le => order.comp(keymaker)(le, e) match { // PartialFunction[(Int,order.unique),Exception]
          case i if i < 0 => None // ok
          case 0 => if (order.unique) Some(UniqueViolation) else None
          case i if i > 0 => Some(OrderViolation)
        }) -> e
      , ()=> last=Some(e) )
    }
  }

  def assert(): Flow[E, E, _] = {
    Flow[E].via(checkException()).map{ case (oex,e) =>
        oex.foreach{ throw _ }
        e
    }
  }

  def filter(): Flow[E, E, _] = {
    Flow[E].statefulMapConcat( ()=>
    {
      var last = Option.empty[E]
      e =>
        Seq(
          last match {
            case Some(le) => order.comp(keymaker)(le, e) match {
              case i if i < 0 => doAfter( ()=>last, ()=>last=Some(e) )
              case 0 => if (order.unique) None else doAfter( ()=>last, ()=>last=Some(e) )
              case i if i > 0 => None
            }
            case None => doAfter( ()=>last, ()=>last=Some(e) )
          }
        )
    }).collect { case Some(e) => e }
  } // .assert(keymaker)
}

