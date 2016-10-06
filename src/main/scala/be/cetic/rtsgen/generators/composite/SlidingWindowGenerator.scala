/*
 * Copyright 2106 Cetic ASBL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package be.cetic.rtsgen.generators.composite

import be.cetic.rtsgen.config.{GeneratorFormat, Model}
import be.cetic.rtsgen.generators.{Generator, TimeToJson}
import be.cetic.rtsgen.timeseries.TimeSeries
import be.cetic.rtsgen.timeseries.composite.SlidingWindowTimeSeries
import org.apache.commons.math3.stat.StatUtils
import org.joda.time.Duration
import spray.json.{JsObject, JsString, JsValue, _}

/**
  * A generator for [[be.cetic.rtsgen.timeseries.composite.SlidingWindowTimeSeries]].
  */
class SlidingWindowGenerator(name: Option[String],
                             val aggregator: String,
                             val generator: Either[String, Generator[Any]],
                             val duration: Duration) extends Generator[Double](name, "window")
{
   override def timeseries(generators: (String) => Generator[Any]) = {

      val aggregation = { x: Seq[(Duration, Double)] => aggregator match {
         case "sum" => x.map(_._2).sum
         case "product" => x.map(_._2).sum
         case "min" => x.map(_._2).min
         case "max" => x.map(_._2).max
         case "mean" => x.map(_._2).sum / x.size
         case "median" => StatUtils.percentile(x.map(_._2).toArray, 50)
         case _ => x.map(_._2).sum / x.size
      }}

      val d = new Duration(duration)

      val base = Model.generator(generators)(generator).timeseries(generators) match {
         case t: TimeSeries[Double] => t
      }

      SlidingWindowTimeSeries[Double](base, duration, aggregation)
   }

   override def toString = "SlidingWindowGenerator(" + name + "," + aggregator + "," + generator + "," + duration + ")"

   override def equals(o: Any) = o match {
      case that: SlidingWindowGenerator => that.name == this.name &&
         that.aggregator == this.aggregator &&
         that.generator == this.generator &&
         that.duration == this.duration
      case _ => false
   }

   override def toJson: JsValue = {
      val _generator = (generator match {
         case Left(s) => s.toJson
         case Right(g) => g.toJson
      }).toJson

      var t = Map(
         "window-length" -> duration.toJson
      )

      if(name.isDefined)
         t = t.updated("name" , name.get.toJson)

      new JsObject(t)
   }
}

object SlidingWindowGenerator extends DefaultJsonProtocol with TimeToJson
{
   def apply(value: JsValue): SlidingWindowGenerator = {
      val fields = value.asJsObject.fields

      val name = fields.get("name").map
      {
         case JsString(x) => x
      }

      val generator = fields("generator") match {
         case JsString(s) => Left(s)
         case g => Right(GeneratorFormat.read(g))
      }

      val aggregator = fields("aggregator") match { case JsString(x) => x }
      val duration = fields("window-length").convertTo[Duration]

      new SlidingWindowGenerator(name, aggregator, generator, duration)
   }
}
