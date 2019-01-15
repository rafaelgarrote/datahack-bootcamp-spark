package com.datahack.bootcamp.spark.exercises.wikipedia

import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

// Wikipedia:
// En este ejercicio queremos realizar algunas estadísticas sobre los artículos contenidos en la wikipedia.
// Para ello, lo primero que tienes que hacer es descargarte el siguiente dataset
// de http://alaska.epfl.ch/~dockermoocs/bigdata/wikipedia.dat
// guárdalo en la carpeta resources de tu proyecto con el nombre wikipedia.dat

case class WikipediaArticle(title: String, text: String) {

  // TODO: Este método indica si un artículo contiene alguan mención al lenguaje 'lang'
  // Consejo: no es necesario buscar en el título.
  def mentionsLanguage(lang: String): Boolean = text.split(' ').contains(lang)
}

object WikipediaRanking {

  // Listado de lenguajes de pprogramación que vamos a utilizar para el análisis.
  val langs = List(
    "JavaScript", "Java", "PHP", "Python", "C#", "C++", "Ruby", "CSS",
    "Objective-C", "Perl", "Scala", "Haskell", "MATLAB", "Clojure", "Groovy")

  // TODO: Crea un spark context a partir de su configuración
  val conf: SparkConf = new SparkConf().setMaster("local[*]").setAppName("Wikipidia programming language rank")
  val sc: SparkContext = new SparkContext(conf)

  // TODO: Lee el fichero que te has descargado con los datos de wikipedia, parséalo y crea un RDD que contenga
  // los artículos parseados en objetos WikipediaArticle.
  // Utiliza para ello la clase WikipediaData
  lazy val wikiRdd: RDD[WikipediaArticle] = sc.textFile(WikipediaData.filePath).map(WikipediaData.parse)

  // TODO: Este métdo devuelve el numero de artículos en los que se mencione el lenguaje `lang`.
  // Consejo: considera utilizar el método aggregate
  // Consjeo: utiliza el método `mentionsLanguage` de la clase `WikipediaArticle`
  def occurrencesOfLang(lang: String, rdd: RDD[WikipediaArticle]): Int =
    rdd.filter(_.mentionsLanguage(lang)).aggregate(0)((x, _) => x + 1, _ + _)

  // TODO: utiliza el método `occurrencesOfLang` para crear el ranking de lenguajes
  // (`val langs`) que indique el número de artículos de la Wikipedia  que mencionen cada lenguaje contenidos en la
  // lista de lenguajes al menos una vez. No te olvides de ordenar los lenguajes por su número de ocurrencias
  // en orden descendente.
  // Nota: Esta operación puede llevar algunos segundos.
  def rankLangs(langs: List[String], rdd: RDD[WikipediaArticle]): List[(String, Int)] =
    langs.map(l => (l, occurrencesOfLang(l, rdd))).sortWith(_._2 > _._2)

  // TODO: Crea un índice invertido para el conjunto de los artículos. Mapea para cada lenguaje que artículos
  // lo nombran.
  def makeIndex(langs: List[String], rdd: RDD[WikipediaArticle]): RDD[(String, Iterable[WikipediaArticle])] =
    rdd.flatMap(a => langs.filter(a.mentionsLanguage).map((_, a))).groupByKey

  // TODO: Vuelve a realizar el ranking, pero esta vez utilizando el índice invertido.
  // ¿Notas alguna diferencia de rendimiento?
  // Nota: Esta operación puede llevar algunos segundos.
  def rankLangsUsingIndex(index: RDD[(String, Iterable[WikipediaArticle])]): List[(String, Int)] =
    index.mapValues(_.size).sortBy(-_._2).collect.toList

  // TODO: Utiliza `reduceByKey` para computar el índice y el ranking
  // ¿Notas alguna diferencia de rendimiento?
  // Nota: Esta operación puede llevar algunos segundos.
  // Nota: Este ejercicio sólo hay que hacerlo cuando hayamos visto los Pair RDD
  def rankLangsReduceByKey(langs: List[String], rdd: RDD[WikipediaArticle]): List[(String, Int)] =
    rdd.flatMap(a => langs.filter(a.mentionsLanguage).map((_, 1))).reduceByKey(_ + _).sortBy(-_._2).collect.toList

  def main(args: Array[String]) {

    // Ranking con aggregate
    val langsRanked: List[(String, Int)] = timed("Ejercicio 1: Ranking con aggregate", rankLangs(langs, wikiRdd))
    println(s"Ranking con aggregate: $langsRanked")

    // Creamos el índice
    def index: RDD[(String, Iterable[WikipediaArticle])] = makeIndex(langs, wikiRdd)

    // Ranking con índice invertido
    val langsRanked2: List[(String, Int)] = timed("Ejercicio 2: Ranking con índice invertido", rankLangsUsingIndex(index))
    println(s"Ranking con índice invertido: $langsRanked2")

    // Ranking con reduceByKey
    val langsRanked3: List[(String, Int)] = timed("Ejercicio 3: Ranking con reduceByKeyy", rankLangsReduceByKey(langs, wikiRdd))
    println(s"Ranking con reduceByKeyy: $langsRanked3")

    // Pintamos el tiempo que no ha llevado cada ranking
    println(timing)
    sc.stop()
  }

  val timing = new StringBuffer

  def timed[T](label: String, code: => T): T = {
    val start = System.currentTimeMillis()
    val result = code
    val stop = System.currentTimeMillis()
    timing.append(s"Processing $label took ${stop - start} ms.\n")
    result
  }
}

