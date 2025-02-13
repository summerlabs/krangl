package krangl.test

import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import krangl.*
import org.apache.commons.csv.CSVFormat
import org.junit.Test
import java.io.File
import java.io.FileReader
import java.io.StringReader
import java.sql.DriverManager
import java.sql.Statement
import java.time.LocalDateTime

/**
 * @author Holger Brandl
 */

class CsvReaderTests {

    @Test
    fun `skip lines and read file without header`() {
        val dataFile = File("src/test/resources/krangl/data/headerless_with_preamble.txt")
        val predictions = DataFrame.readDelim(FileReader(dataFile), skip = 7, format = CSVFormat.TDF)

        predictions.apply {
            nrow shouldBe 13
            names shouldBe listOf("X1", "X2", "X3")
            head().print()
        }
    }


    @Test
    fun testTornados() {
        val tornandoCsv = File("src/test/resources/krangl/data/1950-2014_torn.csv")

        val df = DataFrame.readCSV(tornandoCsv)

        // todo continue test here
    }

    @Test
    fun `it should read a url`() {
        val df =
            DataFrame.readCSV("https://raw.githubusercontent.com/holgerbrandl/krangl/master/src/test/resources/krangl/data/1950-2014_torn.csv")
        assert(df.nrow > 2)
    }

    @Test
    fun `it should read and write compressed and uncompressed tables`() {
        //        createTempFile(prefix = "krangl_test", suffix = ".zip").let {
        //            sleepData.writeCSV(it)
        //            println("file was $it")
        //            DataFrame.readCSV(it).nrow shouldBe sleepData.nrow
        //        }

        createTempFile(prefix = "krangl_test", suffix = ".txt").let {
            sleepData.writeCSV(it, format = CSVFormat.TDF.withHeader())
            DataFrame.readCSV(it, format = CSVFormat.TDF.withHeader()).nrow shouldBe sleepData.nrow
        }
    }

    @Test
    fun `it should have the correct column types`() {

        val columnTypes = NamedColumnSpec(
            "a" to ColType.String,
            "b" to ColType.String,
            "c" to ColType.Double,
            "e" to ColType.Boolean,
            "f" to ColType.Long,
            ".default" to ColType.Int

        )

        val dataFrame =
            DataFrame.readCSV("src/test/resources/krangl/data/test_header_types.csv", colTypes = columnTypes)
        val cols = dataFrame.cols
        assert(cols[0] is StringCol)
        assert(cols[1] is StringCol)
        assert(cols[2] is DoubleCol)
        assert(cols[3] is IntCol)
        assert(cols[4] is BooleanCol)
        assert(cols[5] is LongCol)
    }

    @Test
    fun `it should read csv with compact column spec`() {
        val dataFrame = DataFrame.readCSV(
            "src/test/resources/krangl/data/test_header_types.csv",
            colTypes = CompactColumnSpec("s?dibl")
        )

        val cols = dataFrame.cols
        assert(cols[0] is StringCol)
        assert(cols[1] is StringCol)
        assert(cols[2] is DoubleCol)
        assert(cols[3] is IntCol)
        assert(cols[4] is BooleanCol)
        assert(cols[5] is LongCol)
    }

    val customNaDataFrame by lazy {
        DataFrame.readCSV(
            "src/test/resources/krangl/data/custom_na_value.csv",
            format = CSVFormat.DEFAULT.withHeader().withNullString("CUSTOM_NA")
        )
    }


    @Test
    fun `it should have a custom NA value`() {
        val cols = customNaDataFrame.cols
        assert(cols[0][0] == null)
    }

    @Test
    fun `it should peek until it hits the first N non NA values and guess IntCol`() {
        val cols = customNaDataFrame.cols
        assert(cols[0] is IntCol)

    }

    @Test
    fun `it should read fixed-delim data`() {
        // references
        //https://issues.apache.org/jira/browse/CSV-272
        //https://github.com/GuiaBolso/fixed-length-file-handler
        //https://dev.to/leocolman/handling-fixed-length-files-using-a-kotlin-dsl-1hm1

        val content = """
1         Product 1    Pa wafer                 7.28571        25
2         Product 2    Pb wafer                 NA             25
3         Product 3    test wafer               0.42857        25
    """.trim().trimIndent()


        val format = listOf(
            "Process Flow" to 10,
            "Product ID" to 10,
            "Product Name" to 25,
            "Start Rate" to 10,
            "Lot Size" to 10
        )
        val df = DataFrame.readFixedWidth(StringReader(content), format)
        df.print()
        df.schema()

        df.ncol shouldBe format.size
        df["Start Rate"][1] shouldBe null
    }
}

class BuilderTests {

    @Test
    fun `it should download and cache flights data locally`() {
        if (flightsCacheFile.exists()) flightsCacheFile.delete()
        (flightsData.nrow > 0) shouldBe true
    }

    enum class Engine { Otto, Other }
    data class Car(val name: String, val numCyl: Int?, val engine: Engine)

    @Test
    fun `it should convert objects into data-frames`() {

        val myCars = listOf(
            Car("Ford Mustang", null, Engine.Otto),
            Car("BMW Mustang", 3, Engine.Otto)
        )

        val carsDF = myCars.deparseRecords {
            mapOf(
                "model" to it.name,
                "motor" to it.engine,
                "cylinders" to it.numCyl
            )
        }

        carsDF.nrow shouldBe 2
        carsDF.names shouldBe listOf("model", "motor", "cylinders")

        // use enum order for sorting
        columnTypes(carsDF).print()

        //todo make sure that enum ordinality is used here for sorting
        carsDF.sortedBy { rowNumber }
        //        carsDF.sortedBy { it["motor"] }
        carsDF.sortedBy { it["motor"].asType<Engine>() }
        carsDF.sortedBy { it["motor"].map<Engine> { it.name } }
    }

    @Test
    fun `it should convert object with extractor patterns`() {
        sleepPatterns.deparseRecords(
            "foo" with { awake },
            "bar" with { it.brainwt?.plus(3) }
        ).apply {
            print()
            schema()
            names shouldBe listOf("foo", "bar")
        }
    }


    @Test
    fun `it should coerce lists and iterables to varargs when building inplace data-frames`() {
        dataFrameOf("foo")(listOf(1, 2, 3)).nrow shouldBe 3
        dataFrameOf("foo")(listOf(1, 2, 3).asIterable()).nrow shouldBe 3
        dataFrameOf("foo")(listOf(1, 2, 3).asSequence()).nrow shouldBe 3
    }

    @Test
    fun `it should not allow to create an empty data-frame with dataFrameOf`() {
        // none

//        dataFrameOf(StringCol("user"), DoubleCol("salary"))

        shouldThrow<IllegalArgumentException> {
            dataFrameOf("user", "salary")()
        }

        // but the long syntax must work
        dataFrameOf(StringCol("user", emptyArray()), DoubleCol("salary", emptyArray())).apply {
            nrow shouldBe 0
            ncol shouldBe 2
        }
    }


    @Test
    fun `it should enforce complete data when building inplace data-frames`() {


        // too few
        shouldThrow<IllegalArgumentException> {
            dataFrameOf("user", "salary")(1)
        }

        // too many
        shouldThrow<IllegalArgumentException> {
            dataFrameOf("user", "salary")(1, 2, 3)
        }

    }


    @Test
    fun `it should convert row records in a data frame`() {
        val records = listOf(
            mapOf("name" to "Max", "age" to 23),
            mapOf("name" to "Anna", "age" to 42)
        )

        dataFrameOf(records).apply {
            print()
            nrow shouldBe 2
            names shouldBe listOf("name", "age")
        }
    }

    // https://github.com/holgerbrandl/krangl/issues/84
    @Test
    fun `it should support mixed numbers in column`() {
        val sales = dataFrameOf("product", "sales")(
            "A", 32,
            "A", 12.3,
            "A", 24,
            "B", null,
            "B", 44
        )


        sales.apply {
            schema()
            print()

            this["sales"] should beInstanceOf<DoubleCol>()
        }
    }
}


class JsonTests {


    @Test
    fun `it should read json data from url`() {
        val df = DataFrame.fromJson("https://raw.githubusercontent.com/vega/vega/master/docs/data/movies.json")

        df.apply {
            nrow shouldBe 3201
            names.last() shouldBe "IMDB Votes"
        }
    }

    @Test
    fun `it should read json data from json string`() {
        val df = DataFrame.fromJsonString(
            """
            {
                "cars": {
                    "Nissan": [
                        {"model":"Sentra", "doors":4},
                        {"model":"Maxima", "doors":4},
                        {"model":"Skyline", "doors":2}
                    ],
                    "Ford": [
                        {"model":"Taurus", "doors":4},
                        {"model":"Escort", "doors":4, "seats":5}
                    ]
                }
            }
            """
        )

        df.apply {
            schema()
            print()
            nrow shouldBe 5
            names shouldBe listOf("cars", "model", "doors", "seats")
        }
    }


    @Test
    fun `it should read incomplete json data from json string`() {
        val df = DataFrame.fromJsonString(
            """
            {
               "Nissan": [
                        {"model":"Sentra", "doors":4},
                        {"model":"Maxima", "doors":4},
                        {"model":"Skyline", "seats":9}
                    ],
            }
            """
        )

        df.apply {
            schema()
            print()
            nrow shouldBe 3
            names shouldBe listOf("_id", "model", "doors", "seats")
        }
    }


    @Test
    fun `it should correctly parse long attributes`() {
        val df = DataFrame.fromJsonString(""" {"test":1612985220914} """)
        df.print()
        df.schema()

        val df3 = DataFrame.fromJsonString(""" {"test":1612985220914, "bar":23} """)
        df3.ncol shouldBe 2
        df3.print()
        df.schema()

        val df2 = DataFrame.fromJsonString("""[{"test":1612985220914},{"test":1612985220914}]""")
        df2.names shouldBe listOf("test")
        df2.schema()
    }


    @Test
    fun `it should convert numerical data-frames to matrices, but should fail for mixed type dfs`() {
        shouldThrow<IllegalArgumentException> { irisData.toDoubleMatrix() }
        shouldThrow<IllegalArgumentException> { irisData.toFloatMatrix() }

        irisData.remove("Species").toDoubleMatrix().apply {
            size shouldBe 4
            first().size shouldBe irisData.nrow
        }
    }
}

class DataBaseTests {

    @Test
    fun `it should parse a table from a database into a dataframe`() {
//        Class.forName("org.postgresql.Driver")

        val conn = DriverManager.getConnection("jdbc:h2:mem:")

        val stmt: Statement = conn.createStatement();


        val setupTmpTable = """
            CREATE TABLE cars(id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(255), price INT);
            INSERT INTO cars(name, price) VALUES('Audi', 52642);
            INSERT INTO cars(name, price) VALUES('Mercedes', 57127);
            INSERT INTO cars(name, price) VALUES('Skoda', 9000);
            INSERT INTO cars(name, price) VALUES('Volvo', 29000);
            INSERT INTO cars(name, price) VALUES('Bentley', 350000);
            INSERT INTO cars(name, price) VALUES('Citroen', 21000);
            INSERT INTO cars(name, price) VALUES('Hummer', 41400);
            INSERT INTO cars(name, price) VALUES('Volkswagen', 21600);
        """.trimIndent()

        stmt.execute(setupTmpTable)

        val rs = stmt.executeQuery("SELECT * FROM cars;")

        // convert into DataFrame
        val carsDf: DataFrame = DataFrame.fromResultSet(rs)

        carsDf.apply {
            schema()
            nrow shouldBe 8
            ncol shouldBe 3
        }
    }

    @Test
    fun `it should read dates from a database`() {
//        Class.forName("org.postgresql.Driver")

        val conn = DriverManager.getConnection("jdbc:h2:mem:")

        val stmt: Statement = conn.createStatement();


        val setupTmpTable = """
            CREATE TABLE birthdays(id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR  (255), birthday TIMESTAMP );
            
            INSERT INTO birthdays(name, birthday) VALUES('Max', '2021-01-01 00:00:00');
            INSERT INTO birthdays(name, birthday) VALUES('Anna', '2021-01-01 12:00:00');
        """.trimIndent()

        stmt.execute(setupTmpTable)

        val rs = stmt.executeQuery("SELECT * FROM birthdays;")

        // convert into DataFrame
        val birthdaysDf: DataFrame = DataFrame.fromResultSet(rs)

        birthdaysDf.apply {
            schema()
            nrow shouldBe 2
            ncol shouldBe 3
            get("BIRTHDAY").values().first() should beInstanceOf<LocalDateTime>()
        }
    }
}
