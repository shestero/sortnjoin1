import akka.NotUsed
import akka.stream.Materializer
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}

import scala.concurrent.{ExecutionContext, Future}

import DoAfter._
import Enrich._

object SortedJoin {
  def apply[E1,M1,E2,M2,K](st1: Source[E1,M1], extractor1: (E1)=>K,
                           st2: Source[E2,M2], extractor2: (E2)=>K, order: StreamOrder[K])
                          (implicit ordering: Ordering[K], ec: ExecutionContext, mat: Materializer) =
    new SortedJoin[E1,M1,E2,M2,K](st1,extractor1,st2,extractor2,order)(ordering,ec,mat)
}

class SortedJoin[E1,M1,E2,M2,K](st1: Source[E1,M1], extractor1: (E1)=>K,
                                st2: Source[E2,M2], extractor2: (E2)=>K, order: StreamOrder[K])
                               (implicit ordering: Ordering[K], ec: ExecutionContext, mat: Materializer)
{
  protected def lowLevelJoin(q1: SinkQueueWithCancel[E1], q2: SinkQueueWithCancel[E2]): Source[(Option[K],(Option[E1],Option[E2])),NotUsed] =
  {
    Source.unfoldAsync((Option.empty[E1],q1,Option.empty[E2],q2)){
      case (o1: Option[E1], q1: SinkQueueWithCancel[E1], o2: Option[E2], q2: SinkQueueWithCancel[E2]) =>

        val f = for {
          get1 <- o1.map(e1=>Future(Some(e1))).getOrElse(q1.pull().recover(_=>None)) // if (o1.isDefined) Future(o1) else q1.pull()
          get2 <- o2.map(e2=>Future(Some(e2))).getOrElse(q2.pull().recover(_=>None))
        } yield {
          (get1.map(enrich(extractor1)),get2.map(enrich(extractor2))) match {
            case (Some((k1,e1)), Some((k2,e2))) =>
              val cmp = ordering.compare(k1,k2)
              if (cmp<0)
                Some(k1)->(Right(e1),Left(get2))
              else if (cmp>0)
                Some(k2)->(Left(get1),Right(e2))
              else {
                assert(cmp==0)
                assert(k1==k2)
                Some(k1)->(Right(e1),Right(e2))
              }
            case (Some((k1,e1)),None) =>
              Some(k1)->(Right(e1),Left(None))
            case (None,Some((k2,e2))) =>
              Some(k2)->(Left(None),Right(e2))
            case (None,None)     =>
              None->(Left(None),Left(None))
          }
        }

        val ff = f.map {
          case (opk,(Right(e1),Right(e2))) =>
            false->(None,None)->(opk->(Some(e1),Some(e2)))
          case (opk,(Right(e1), Left(o2) )) =>
            false->(None,o2)  ->(opk->(Some(e1),None))
          case (opk,( Left(o1) ,Right(e2))) =>
            false->(o1,None)  ->(opk->(None,Some(e2)))
          case (opk,( Left(o1) , Left(o2) )) =>
            assert(!opk.isDefined)
            true->(o1,o2)->(opk->(None,None))
        }
        f.failed.foreach(th=>println(s"FUTURE0 FAILED! ${th.getMessage}"))

        for { ((stop,(o1,o2)),okoe1oe2) <- ff } yield Option.unless(stop)((o1,q1,o2,q2) -> okoe1oe2)
    }
  }

  def fullJoin[M](combine: (M1,M2)=>M): Source[(K,(Seq[E1],Seq[E2])),M] =
  {
    val stop = Source.single(None->(None,None)) // end of stream to flush buffers
    val ((m1,q1),(m2,q2)) = ( st1.toMat(Sink.queue())(Keep.both).run(), st2.toMat(Sink.queue())(Keep.both).run() )
    (lowLevelJoin(q1,q2)++stop).map {
      case (ok,(op1,op2))=> ok->(op1.toList,op2.toList)
    }.statefulMapConcat(()=> {
      var k = Option.empty[K]
      val (sq1, sq2) = (collection.mutable.ListBuffer.empty[E1], collection.mutable.ListBuffer.empty[E2])
      e: (Option[K],(Seq[E1],Seq[E2])) => {
        val (newk, (l1, l2)) = e
        doAfter( () =>
          Option.unless(k==newk)(
            doAfter( () => k.map(_ -> (sq1.toSeq, sq2.toSeq)),
              { () =>
                k = newk
                sq1.clear(); sq2.clear()

                //(sq1,sq2).map(clear)
                //(sq1,sq2).toList.transpose.foreach(_.clear)
              })
          ).flatten.toList // toIterable
          ,{ () =>
            sq1 ++= l1; sq2 ++= l2
            //(sq1,sq2).productIterator.zip((l1, l2).productIterator).map{ case (s,l)=>s++=l }
            //((sq1,sq2),(l1,l2)).zip.map
          })
      }
    }).mapMaterializedValue(_=>combine(m1,m2))
  }


  def join[M](combine: (M1,M2)=>M): Source[(K,(E1,E2)),M] =
  //fullJoin(st1,order1, st2,order2).collect{ case(k,(sq1,sq2)) if (!sq1.isEmpty)&&(!sq2.isEmpty) => k->(sq1.head,sq2.head) }
  {
    val ((m1,q1),(m2,q2)) = ( st1.toMat(Sink.queue())(Keep.both).run(), st2.toMat(Sink.queue())(Keep.both).run() )
    lowLevelJoin(q1,q2).collect {
      case (Some(k),(Some(e1),Some(e2))) => k->(e1,e2)
    }.mapMaterializedValue(_=>combine(m1,m2))
  }

  def leftJoin[M](combine: (M1,M2)=>M): Source[(K,(E1,Seq[E2])),M] =
    fullJoin(combine).mapConcat{ case(k,(sq1,sq2)) => sq1.map(e1=>k->(e1,sq2)) }

  def rightJoin[M](combine: (M1,M2)=>M): Source[(K,(Seq[E1],E2)),M] =
    fullJoin(combine).mapConcat{ case(k,(sq1,sq2)) => sq2.map(e2=>k->(sq1,e2)) }

}
