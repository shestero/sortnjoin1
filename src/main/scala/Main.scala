import java.util.Comparator

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.typesafe.config.ConfigFactory

object Main extends App {
  val app = "sortnjoin1"
  println(s"Hello, I'm $app (Sort'n'Join)")

  val config = ConfigFactory.parseString("akka.loglevel = DEBUG")
  implicit val system = ActorSystem(app, config)
  implicit val ec = system.dispatcher
  // implicit val materializer = ActorMaterializer()
  println("Akka: "+system.name)

  val data = Seq(1,5,4,7,3, -6,3,5,6,5,4 )
  val data2 = Seq(4,5,7,3, -6,3,5,5,8 )
  val src = Source.fromIterator(()=>data.iterator)

  src.runWith( Sink.seq ).foreach( r=>println("Src1="+r) )
  src.runWith( Sink.seq ).foreach( r=>println("Src2="+r) )

  implicit val keymaker: Int=>Int = identity[Int]
  implicit val streamOrder = StreamOrder[Int]()

  val sorted1 = src.via( StreamSort().fullSort() )
  sorted1.runWith( Sink.seq ).foreach( r=>println("S1="+r) )

  val sorted2 = src.via( StreamSort()(identity[Int],StreamOrder[Int](OrderDirection.DESC)).fullSort() )
  sorted2.runWith( Sink.seq ).foreach( r=>println("S2="+r) )
  sorted2.runWith( Sink.seq ).foreach( r=>println("S2="+r) )

  val sorted3 = src.via( StreamSort().filter() )
  sorted3.runWith( Sink.seq ).foreach( r=>println("S3="+r) )

  Thread.sleep(300)
  println("check#1")
  sorted1.via( StreamSort().assert() ).runForeach(println).failed.foreach(println)
  Thread.sleep(300)
  //println("check#2")
  //src.via( SortedStream().check() ).runForeach(println).failed.foreach(println)

  val sorted4 = Source.fromIterator(()=>data2.iterator).via( StreamSort().filter() )
  sorted4.runWith( Sink.seq ).foreach( r=>println("S4="+r) )
  val sorted5 = src.via( StreamSort[Int,Int].fullSort() )
  sorted5.runWith( Sink.seq ).foreach( r=>println("S5="+r) )
  //sorted5.runWith( Sink.seq ).foreach( r=>println("S5="+r) )

  Thread.sleep(100)
  val j1 = SortedJoin(sorted4, identity[Int], sorted5, identity[Int], streamOrder).leftJoin(Keep.none).take(30)
  j1.runForeach( r=>println("J1="+r) ).failed.foreach(e=>println("J1 catched error: "+e.getMessage))

  val j2 = SortedJoin(sorted4, identity[Int], sorted5, identity[Int], streamOrder).fullJoin(Keep.none).take(30)
  j2.runForeach( r=>println("J2="+r) ).failed.foreach(e=>println("J2 catched error: "+e.getMessage))

  Thread.sleep(200)

  println
  val data8 = Seq(4,5,3,7, 6,5,4,5,8 )
  val src8 = Source.fromIterator(()=>data8.iterator)
  src8.runWith( Sink.seq ).foreach( r=>println("Src8="+r) )
  // implicit val o2 = Ordering[(Int,Long)]
  val sorted8 = src8.via( StreamSort().partSort(4) ).via( StreamSort[(Int,Long),(Int,Long)]()(identity,StreamOrder[(Int,Long)]()).checkException() )
  sorted8.runWith( Sink.seq ).foreach( r=>println("S8="+r) )

  Thread.sleep(1800)
  println("Bye")
}
