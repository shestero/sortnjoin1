object DoAfter {
  def doAfter[R](main:()=>R, after:(R)=>Any): R = {
    val result: R = main ()
    after(result)
    result
  }
  def doAfter[R](main:()=>R, after:()=>Any): R = {
    val result: R = main ()
    after()
    result
  }
}
