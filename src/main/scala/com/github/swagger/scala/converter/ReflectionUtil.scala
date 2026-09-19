package com.github.swagger.scala.converter

object ReflectionUtil {

  /** The class of the companion object of `cls`, or `cls` itself if it is already a companion (module) class. The class is loaded without
    * running its static initializers.
    */
  def companionObjectClass(cls: Class[_]): Class[_] = {
    val cname = cls.getName
    if (cname.endsWith("$")) cls
    else Class.forName(cname + "$", false, Thread.currentThread.getContextClassLoader)
  }

  /** The companion object instance of `cls` (the value of its `MODULE$` field). */
  def companionObject(cls: Class[_]): AnyRef = {
    companionObjectClass(cls).getField("MODULE$").get(None.orNull)
  }
}
