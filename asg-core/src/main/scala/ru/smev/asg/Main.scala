package ru.smev.asg
import java.nio.file.Path
import ru.smev.asg.verification.*
import scala.util.control.NonFatal

/** Offline laboratory CLI. It cannot approve, deploy or execute a translated public-service request. */
object Main:
  def main(args: Array[String]): Unit =
    if args.length != 3 then
      System.err.println("Usage: asg-core DATA.ttl SHAPES.ttl CONSTRAINTS.rq")
      sys.exit(2)
    try
      val evidence = Verification.verify(Path.of(args(0)), Path.of(args(1)), Path.of(args(2)))
      evidence.bindings.toVector.sortBy(_._1).foreach((k, v) => println(s"sha256.$k=$v"))
      evidence.checks.foreach(c => println(s"${c.id}=${c.status}; scope=${c.scope}; issues=${c.details.size}"))
      sys.exit(if evidence.passes(Verification.Required) then 0 else 1)
    catch
      case NonFatal(e) =>
        System.err.println(s"Verification unavailable: ${e.getClass.getSimpleName}")
        sys.exit(2)
