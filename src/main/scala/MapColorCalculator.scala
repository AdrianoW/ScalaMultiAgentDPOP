/**
 * Created by adrianowalmeida on 27/08/15.
 */

import akka.actor._
import com.typesafe.config.ConfigFactory
import scala.collection.mutable.ArrayBuffer
import scala.io.Source

object MapColorCalculator extends App {

  // this is going to start the calculus
  val fileToRead = "nome do arquivo"
  calculate(fileToRead)

  // define the function
  def calculate(fileToRead: String): Unit = {

    // read the file and get the graph.txt
    println("Lido o arquivo com o grafo")
    val info = Source.fromURL(getClass.getResource("/graph.txt")).getLines()
    createGraph(info)


    val customConf = ConfigFactory.parseString("""
      akka {
        loglevel = "INFO"
      } """)
    // ConfigFactory.load sandwiches customConfig between default reference
    // config and default overrides, and then resolves it.
    val system = ActorSystem("MapColorCalculator", ConfigFactory.load(customConf))

    // create the listener that will output the calulus
    val listener = system.actorOf(Props[Listener], name="OutputListener")

    // create the master that will handle the workers
    val master = system.actorOf(Props(new Master("teste", listener)), name="Master")

    // send the calculate message
    master ! START
  }

  def createGraph(lines: Iterator[String]) = {
    // get all the actors
    lines.takeWhile( _ )
  }

  // messages contents
  case class UtilValue(data: List[List[Any]], colNames:List[String])


  // define the colors
  sealed trait Color
  case object GREEN extends Color
  case object BLUE extends Color
  case object RED extends Color
  case object WHITE extends Color
  val colors = Seq(GREEN, BLUE, RED, WHITE)
  val colorPerm = colors.map(c => List(c,c)).toList ::: colors.combinations(2).toList
  // a row of combinations
  case class Row(colorVals: List[Color],utility: Int)

  // define the messages that are going to be exchanged
  sealed trait MapMessages
  case class UTIL(cube: Option[UMatrix]) extends MapMessages
  case class VALUE(cube: UMatrix) extends MapMessages
  case class START() extends MapMessages
  case class FINISH(result: Map[ActorRef, Color]) extends MapMessages
  case class RESULT(result: ArrayBuffer[Map[ActorRef, Color]]) extends MapMessages
  case class SETUP_NODES(parents: Option[List[ActorRef]], children: Option[List[ActorRef]], pseudo_parents: Option[List[ActorRef]]) extends MapMessages

  // the output actor
  class Listener extends Actor with ActorLogging {
    def receive = {
      case RESULT(result) ⇒
        log.info("Resultado %s".format(result))

        // shut down the whole system
        context.system.shutdown()
    }
  }

  // master actor that will coordinate the others
  class Master(grahInfo: String, listener: ActorRef) extends Actor with ActorLogging {

    // log the start of things
    log.info("Starting calculation")

    var children: ArrayBuffer[ActorRef] = ArrayBuffer()
    var childResult: ArrayBuffer[Map[ActorRef, Color]] = ArrayBuffer()

    // create the agents
    createAgents(grahInfo)

    // start processing
    self ! START()

    def receive = {
      case START() =>
        // send the start command to all children
        for(ch <- this.children) {
          ch ! START()
        }
        log.debug("Started all actors")

      case FINISH(result: Map[ActorRef, Color]) =>
        // save the result
        childResult.append(result)

        // Send the result to the listener if all have finished
        if(childResult.length == children.length) {
          log.debug("Killing all")
          listener ! RESULT(childResult)
          // Stops this actor and all its supervised children
          context.stop(self)
        }
    }

    def createAgents(grahInfo: String) = {
      // create the agents and return them
      log.debug("Agentes sendo criados " + grahInfo)

      // just as a test, create actors
      val RS = context.actorOf(Props(new Nodes("RS")), "RS")
      val SC = context.actorOf(Props(new Nodes("SC")), "SC")
      val PR = context.actorOf(Props(new Nodes("PR")), "PR")
      val SP = context.actorOf(Props(new Nodes("SP")), "SP")
      val MS = context.actorOf(Props(new Nodes("MS")), "MS")

      // setup the actors
      RS ! SETUP_NODES( Some(List(SC)), None, None)
      //RS ! SETUP_NODES( Some(List(SC)), None, Some(List(PR)))
      SC ! SETUP_NODES( Some(List(PR)), Some(List(RS)), None)
      //PR ! SETUP_NODES( None, Some(List(SC)), None )
      PR ! SETUP_NODES( Some(List(SP)), Some(List(SC)), Some(List(MS)) )
      SP ! SETUP_NODES( None, Some(List(PR,MS)), None )
      MS ! SETUP_NODES( Some(List(SP)), None, None )

      children.append(RS)
      children.append(PR)
      children.append(SC)
      children.append(SP)
      children.append(MS)

      // XUNXO para dar tempo de iniciar
      Thread.sleep(100)

    }
  }

  // the nodes classes
  class Nodes(name: String) extends Actor with ActorLogging {

    log.debug("Created %s".format(self))

    var parents: List[ActorRef] = _
    var pseudoParents: List[ActorRef] = _
    var children: List[ActorRef] = _
    var color: Color = _
    var childrenMatrixes: Map[ActorRef, UMatrix] = Map()
    var parentsColors: UMatrix = _
    var numReceived = 0
    var localMatrix: UMatrix = _
    var myCube: UMatrix = _
    var isSetup = false

    def receive = {
      case UTIL(cube) =>

        val utilMatrix = cube match {
          case Some(matrix) =>
            // save the matrix from that came from child
            this.numReceived += 1
            this.childrenMatrixes += sender() -> matrix
            //this.childrenMatrixes = (this.childrenMatrixes.toSeq ++ matrix.toSeq)
            //  .groupBy(_._1)
            //  .mapValues(_.map(_._2).sum)

            // message came from children. calculate new values
            log.debug("Recebida matrix")
            log.debug(s"\nDe: %s \nMatrix: \n%s".format(sender,matrix))

            // if received all, calculate the utility for this actor with the children
            if (this.numReceived == this.children.length) {
              // aggregate both
              Some(calculateUtil(this.childrenMatrixes))
            } else {
              None
            }
          case _ => {
            // this is the case where there is no children. Start send this message, with empty matrix from the child
            Some(calculateUtil(this.childrenMatrixes))
          }
        }

        // send the utilmatrix if it was calculated
        if( utilMatrix.isEmpty == false ) {
          // save the local view
          this.localMatrix = utilMatrix.get

          // send the cube map to all parents, removing its own information from the cube
          for(par <- this.parents) {
            // save my cube
            myCube =  utilMatrix.get.getMostUtil
            log.debug(s"My CUBE \n%s".format(myCube))
            par ! UTIL(Some(myCube.dropCol(List(self)).distinct))
          }

          // check if this is the root of all the tree
          if (parents.isEmpty) {

            // calculate the most util
            val mostUtil = this.localMatrix.getMostUtil
            log.debug(s"Root decision table \n%s".format(mostUtil))

            // select this node column, get the color from the row data
            this.color = mostUtil.select(List(self)).data.head.colorVals.head
            log.debug(s"COR: %s".format(this.color))

            // send the value to its children
            for(chi <- this.children) {
              chi ! VALUE( mostUtil.where(self)(this.color).select(childrenMatrixes(chi).columns) )
            }
            context.parent ! FINISH(Map(self -> this.color))
          }
        }
      case VALUE(cube) => {
          // message came from parent. check the color
          //this.color = cube(self)
          log.debug("%s recebeu decisao \n %s".format(self, cube))

          // save the parent color
          this.parentsColors = cube

          // check if we have all the colors of the parents
          //var filtered: UMatrix = this.localMatrix
          //var idx: Int = 0
          // filter the data for each parent
          //for( (k,v) <- this.parentsColors) {
          //  filtered = filtered.where(k)(v)
          //}
          //println(filtered)
          //println(filtered.getMostUtil(self))
          //println(filtered.getMostUtil(self).toList.sortBy(-_._2).head._1)
          val bestVals = myCube.join(this.parentsColors, None)
          log.debug(s"Best Value Matrix \n %s".format(bestVals))

          val mostUtil = bestVals.getMostUtil

          this.color = mostUtil.select(List(self)).data.head.colorVals.head
          log.debug(s"COR: %s".format(this.color))

          // send the chosen color to children if they exist
          for(chi <- this.children) {
            chi ! VALUE( mostUtil.where(self)(this.color).select(childrenMatrixes(chi).columns) )
          }

          // if this is the leaf, kill the system
          context.parent ! FINISH(Map(self -> this.color))
      }
      case START() => {
        // start processing
        // check if this is the leaf
        if (this.children.isEmpty) {
          // check who the parent are and calculate probable values
          log.debug("%s É uma folha. iniciando os calculos".format(self))

          // calculate things
          self ! UTIL(None)
        }

      }
      case SETUP_NODES(parents, children, pseudoParents) => {
        log.debug("Setting up %s with parents %s and children %s"
          .format(self, parents.getOrElse(""), children.getOrElse("")))
        this.parents = parents.getOrElse(List())
        this.children = children.getOrElse(List())
        this.pseudoParents = pseudoParents.getOrElse(List())
        this.isSetup = true
      }
    }

    // the utility calculator. Substitute this to change the calculus
    def funcUtil(val1: Color, val2 : Color): Int = {
      if( val1 == val2 ){
        0
      } else {
        1
      }
    }

    // combination of all possible outcomes
    def combinations_with_replacement(values: List[Color], permSize: Int) = {
      List.fill(permSize)(values).flatten.combinations(permSize).toList
    }

    // calculate the util matrix
    def calculateUtil(childCube: Map[ActorRef, UMatrix]): UMatrix = {
      // get the nodes names
      var names = List(self)
      if( this.parents.length>0){
        names =  names ::: this.parents
      }

      if( this.pseudoParents.length>0){
        names =  names ::: this.pseudoParents
      }

      // create values: repeated values and permutation of them
      val combinations = combinations_with_replacement(colors.toList, names.size)

      // create the utility comparing current node with the parent. Then accumulate the values as the final utility
      val utilList = ArrayBuffer[Int]()
      for( (combination, i) <- combinations.zipWithIndex) {
        var util = 0
        // check the utility of current node (0) with the others, summing them
        for( v <-combination.tail) {
          // call the util function to calulate utility
          util = util + funcUtil(combination(0), v)
        }

        // add the utility from child and save total util
        //util += childCube.getOrElse(combination(0), 0)
        utilList += util
      }

      // create a list with all combinations and their utility
      val data = combinations zip utilList map {v=> Row(v._1, v._2)}
      var cube = new UMatrix(names, data)

      // create the joined matrix of all utilities
      for( child <- childCube ) {
        cube = cube.join(child._2, None)
      }

      /*// get each of the columns of the combination and create a column
      for( (col, i) <- names.zipWithIndex) {
        val temp =  Panel(Vec(combinations.map(comb => comb(i)).toArray[Color]))
          .setColIndex(Index(col.toString()))
        cube = cube.rconcat(temp, how=index.LeftJoin)
      }*/

      // check if there was a Util matrix from child
      /*childCube match {
        case Some(matrix) => {
          println(s"Recebida matrix de filho %s".format(matrix))
          cube
        }
        case _ => cube
      }*/
      cube
    }
  }

  // holds the cube of values
  class UMatrix (var columns: List[ActorRef], var data: List[Row] ) {
    // easily add a row
    //def append(new_row:List[Any]) = {
    //  this.data ::= new_row
    //}

    def getMostUtil(col_name: ActorRef): Map[Color,Int] = {
      // sort the dataframe and get the most util
      //this.data.sortBy(-_(0).asInstanceOf[Int]).head
      // get the index inside the names list
      val idx = columns.indexOf(col_name)
      data
        // generate (Color, value) pairs
        .map( v => (v.colorVals(idx), v.utility) )
        // group by the color name
        .groupBy( x => x._1 )
        // change from color -> (color, array(val1, val2)) to (color, array)
        .map( v => (v._1, v._2.map(_._2)) )
        // return (color, max value of array)
        .map( v => (v._1, v._2.max ) )
    }

    def getMostUtil() = {
      // returns the lines that have the biggest utility and the least number of colors
      val maxVal = data.map( v=> v.utility).max
      val new_data = data.filter(_.utility == maxVal) //.minBy(x => x.colorVals.distinct.length )

      new UMatrix(this.columns, new_data)
    }

    def where(column: ActorRef)(value: Color):UMatrix = {
      // filter according to the colum and then the value
      val idx = columns.indexOf(column)
      new UMatrix(this.columns,
        data
          .filter( v => v.colorVals(idx) == value )
          .sortBy( x=> -x.utility))
    }

    def select(columnList: List[ActorRef]) = {

      def idxList = columnList.map(i=> columns.indexOf(i))
      new UMatrix(
        idxList.map(columns),
        data.map( l => Row(idxList.map(l.colorVals), l.utility) )
      )
    }

    // return the index of a column
    def getActorIdx(column: String): Int = {
      columns.indexOf(column)
    }

    // print utility
    override def toString() = {
      "==============================================\n" +
        "Columns: \n" + this.columns.toString() +"\n"+
        "Data: \n"+ this.data.map(_ + "\n") +
        "==============================================\n"
    }

    // help utility to drop element from list by index
    def dropIndex[T](xs: List[T], n: List[Int]):List[T] = {
      val ordIdx = n.sorted
      val (l1, _ :: l2) = xs splitAt ordIdx.head
      if (n.tail.isEmpty) l1 ::: l2
      // recursively deals with the list of index
      else dropIndex(l1 ::: l2, ordIdx.tail.map(_ - 1))
    }

    // get the column indexes
    def getColsIdx(cols: List[ActorRef]) = {
      cols.map(i=> this.columns.indexOf(i))
    }

    // drop a column
    def dropCol(cols: List[ActorRef]) = {
      // get the column position and then drop it in all data returning new Umatrix
      val idx1 = getColsIdx(cols)
      new UMatrix( dropIndex(this.columns, idx1),
        this.data.map(l => Row(dropIndex(l.colorVals, idx1), l.utility) ))
    }

    // aggregation function
    def aggSum(a:Int, b:Int): Int = a + b

    // join two matrixes
    def join(b:UMatrix, col: Option[List[ActorRef]]) = joinAgg(b, col, aggSum)

    // aggregate based on a function
    def joinAgg(b:UMatrix, cols: Option[List[ActorRef]], agg: (Int,Int) => Int) = {
      //assert(this.columns.contains(cols) && b.columns.contains(cols), "Column is not present on both matrixes")

      val col_names = cols.getOrElse({
        this.columns.filter(b.columns.contains(_))
      })

      val idx1 = getColsIdx(col_names)
      val idx2 = b.getColsIdx(col_names)

      // join the columns and aggregate the values of utility
      val filtered = data.flatMap(v=>
        b.data.map( t=>
          // do a left join between the first and the second matrix
          if( idx1.map(v.colorVals)== idx2.map(t.colorVals))
            Row(v.colorVals ::: dropIndex(t.colorVals, idx2),
              agg(v.utility, t.utility))
          else
            Row(v.colorVals, -1)
        )
          .filter( _.utility != -1)
      )

      // remove the column
      val columns = this.columns ::: dropIndex(b.columns, idx2)

      new UMatrix(columns, filtered)
    }

    def distinct = new UMatrix(this.columns, this.data.distinct)
  }

}
