object Enrich {
  def enrich[E,U,V](f: (E)=>U, g: (E)=>V) = { e:E => f(e)->g(e) }
  def enrich[E,U](f: (E)=>U) = enrich[E,U,E](f,identity[E])
}
