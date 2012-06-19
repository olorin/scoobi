package com.nicta.scoobi
package impl
package plan
package comp

import data.Data
import io.ConstantStringDataSource
import core.{Emitter, BasicDoFn}
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Gen._

trait DCompData extends Data {
  /**
   * Creation functions
   */
  def load = Load(ConstantStringDataSource("start"))
  def flatten[A](nodes: CompNode*) = Flatten(nodes.toList.map(_.asInstanceOf[DComp[A,Arr]]))
  def parallelDo(in: CompNode) = pd(in)
  def rt = Return("")
  def pd(in: CompNode) = ParallelDo[String, String, Unit](in.asInstanceOf[DComp[String,Arr]], Return(()), fn)
  def cb(in: CompNode) = Combine[String, String](in.asInstanceOf[DComp[(String, Iterable[String]),Arr]], (s1: String, s2: String) => s1 + s2)
  def gbk(in: CompNode) = GroupByKey(in.asInstanceOf[DComp[(String,String),Arr]])
  def mt(in: CompNode) = Materialize(in.asInstanceOf[DComp[String,Arr]])
  def op[A, B](in1: CompNode, in2: CompNode) = Op[A, B, A](in1.asInstanceOf[DComp[A,Exp]], in2.asInstanceOf[DComp[B,Exp]], (a, b) => a)

  lazy val fn = new BasicDoFn[String, String] { def process(input: String, emitter: Emitter[String]) { emitter.emit(input) } }

  /** show the structure without the ids */
  lazy val showStructure = (n: CompNode) => show(n).replaceAll("\\d", "")

  /** show before and after the optimisation */
  def optimisation(node: CompNode, optimised: CompNode) =
    if (show(node) != show(optimised)) "INITIAL: \n"+show(node)+"\nOPTIMISED:\n"+show(optimised) else "no optimisation"

  /**
   * Arbitrary instance for a CompNode
   */
  import scalaz.Scalaz._
  import Gen._

  implicit lazy val arbitraryCompNode: Arbitrary[CompNode] = Arbitrary(sized(depth => genDComp(depth)))

  def genDComp(depth: Int = 1): Gen[CompNode] = lzy(frequency((3, genLoad(depth)),
                                                              (4, genParallelDo(depth)),
                                                              (4, genGroupByKey(depth)),
                                                              (3, genMaterialize(depth)),
                                                              (3, genCombine(depth)),
                                                              (5, genFlatten(depth)),
                                                              (2, genOp(depth)),
                                                              (2, genReturn(depth))))

  def genLoad       (depth: Int = 1) = oneOf(load, load)
  def genReturn     (depth: Int = 1) = oneOf(rt, rt)
  def genParallelDo (depth: Int = 1) = if (depth <= 1) value(parallelDo(load)) else memo(genDComp(depth - 1) map (parallelDo _))
  def genFlatten    (depth: Int = 1) = if (depth <= 1) value(flatten(load)   ) else memo(choose(1, 3).flatMap(n => listOfN(n, genDComp(depth - 1))).map(l => flatten(l:_*)))
  def genCombine    (depth: Int = 1) = if (depth <= 1) value(cb(load)        ) else memo(genDComp(depth - 1) map (cb _))
  def genOp         (depth: Int = 1) = if (depth <= 1) value(op(load, load)  ) else memo((genDComp(depth - 1) |@| genDComp(depth - 1))((op _)))
  def genMaterialize(depth: Int = 1) = if (depth <= 1) value(mt(load)        ) else memo(genDComp(depth - 1) map (mt _))
  def genGroupByKey (depth: Int = 1) = if (depth <= 1) value(gbk(load)       ) else memo(genDComp(depth - 1) map (gbk _))

}