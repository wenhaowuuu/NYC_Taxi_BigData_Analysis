// Databricks notebook source
// STARTER CODE - DO NOT EDIT THIS CELL
import org.apache.spark.sql.functions.desc
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import spark.implicits._
import org.apache.spark.sql.expressions.Window

// COMMAND ----------

// STARTER CODE - DO NOT EDIT THIS CELL
val customSchema = StructType(Array(StructField("lpep_pickup_datetime", StringType, true), StructField("lpep_dropoff_datetime", StringType, true), StructField("PULocationID", IntegerType, true), StructField("DOLocationID", IntegerType, true), StructField("passenger_count", IntegerType, true), StructField("trip_distance", FloatType, true), StructField("fare_amount", FloatType, true), StructField("payment_type", IntegerType, true)))

// COMMAND ----------

// STARTER CODE - YOU CAN LOAD ANY FILE WITH A SIMILAR SYNTAX.
val df = spark.read
   .format("com.databricks.spark.csv")
   .option("header", "true") // Use first line of all files as header
   .option("nullValue", "null")
   .schema(customSchema)
   .load("/FileStore/tables/nyc_tripdata.csv") // the csv file which you want to work with
   .withColumn("pickup_datetime", from_unixtime(unix_timestamp(col("lpep_pickup_datetime"), "MM/dd/yyyy HH:mm")))
   .withColumn("dropoff_datetime", from_unixtime(unix_timestamp(col("lpep_dropoff_datetime"), "MM/dd/yyyy HH:mm")))
   .drop($"lpep_pickup_datetime")
   .drop($"lpep_dropoff_datetime")

// COMMAND ----------

// LOAD THE "taxi_zone_lookup.csv" FILE SIMILARLY AS ABOVE. CAST ANY COLUMN TO APPROPRIATE DATA TYPE IF NECESSARY.

// ENTER THE CODE BELOW
val zones = spark.read
   .format("com.databricks.spark.csv")
   .option("header", "true") // Use first line of all files as header
   .option("nullValue", "null")
   .load("/FileStore/tables/taxi_zone_lookup.csv") // the csv file which you want to work with
   .withColumn("LocationID", col("LocationID").cast("Integer"))

// COMMAND ----------

// STARTER CODE - DO NOT EDIT THIS CELL
// Some commands that you can use to see your dataframes and results of the operations. You can comment the df.show(5) and uncomment display(df) to see the data differently. You will find these two functions useful in reporting your results.
// display(df)
df.show(5) // view the first 5 rows of the dataframe

// COMMAND ----------

// STARTER CODE - DO NOT EDIT THIS CELL
// Filter the data to only keep the rows where "PULocationID" and the "DOLocationID" are different and the "trip_distance" is strictly greater than 2.0 (>2.0).

// VERY VERY IMPORTANT: ALL THE SUBSEQUENT OPERATIONS MUST BE PERFORMED ON THIS FILTERED DATA

val df_filter = df.filter($"PULocationID" =!= $"DOLocationID" && $"trip_distance" > 2.0)
df_filter.show(5)

// COMMAND ----------

// PART 1a: The top-5 most popular drop locations - "DOLocationID", sorted in descending order - if there is a tie, then one with lower "DOLocationID" gets listed first
// Output Schema: DOLocationID int, number_of_dropoffs int 

// Hint: Checkout the groupBy(), orderBy() and count() functions.

// ENTER THE CODE BELOW
val popularDO = df_filter.groupBy("DOLocationID").count().orderBy($"count".desc,$"DOLocationID").withColumnRenamed("count","number_of_dropoffs")

popularDO.show(5)

// COMMAND ----------

// PART 1b: The top-5 most popular pickup locations - "PULocationID", sorted in descending order - if there is a tie, then one with lower "PULocationID" gets listed first 
// Output Schema: PULocationID int, number_of_pickups int

// Hint: Code is very similar to part 1a above.

// ENTER THE CODE BELOW
val popularPU = df_filter.groupBy("PULocationID").count().orderBy($"count".desc,$"PULocationID").withColumnRenamed("count","number_of_pickups")

popularPU.show(5)

// COMMAND ----------

// PART 2: List the top-3 locations with the maximum overall activity, i.e. sum of all pickups and all dropoffs at that LocationID. In case of a tie, the lower LocationID gets listed first.
// Output Schema: LocationID int, number_activities int

// Hint: In order to get the result, you may need to perform a join operation between the two dataframes that you created in earlier parts (to come up with the sum of the number of pickups and dropoffs on each location). 

// ENTER THE CODE BELOW
val popularDO2 = popularDO.withColumnRenamed("count","DOcount")
val popularPU2 = popularPU.withColumnRenamed("count","PUcount")
val topactivity = popularDO2.join(popularPU2, popularDO2("DOLocationID") === popularPU2("PULocationID"))
    .withColumn("number_activities", $"DOcount" + $"PUcount")
    .withColumn("number_activities",$"number_activities".cast("Integer"))
    .withColumnRenamed("DOLocationID","LocationID")
    .orderBy($"number_activities".desc,$"LocationID".asc)
    .drop($"PULocationID")
    .drop($"DOcount")
    .drop($"PUcount")

topactivity.show(3)

// COMMAND ----------

// PART 3: List all the boroughs in the order of having the highest to lowest number of activities (i.e. sum of all pickups and all dropoffs at that LocationID), along with the total number of activity counts for each borough in NYC during that entire period of time.
// Output Schema: Borough string, total_number_activities int

// Hint: You can use the dataframe obtained from the previous part, and will need to do the join with the 'taxi_zone_lookup' dataframe. Also, checkout the "agg" function applied to a grouped dataframe.

// ENTER THE CODE BELOW
val zones2 = zones.select($"LocationID",$"Borough").withColumnRenamed("LocationID","LocationID2")
val zactivity = topactivity.join(zones2, topactivity("LocationID") === zones2("LocationID2"))
    .groupBy($"Borough").agg(sum($"number_activities").as("total_number_activities"))
    .orderBy($"total_number_activities".desc)

zactivity.show

// COMMAND ----------

// PART 4: List the top 2 days of week with the largest number of (daily) average pickups, along with the values of average number of pickups on each of the two days. The day of week should be a string with its full name, for example, "Monday" - not a number 1 or "Mon" instead.
// Output Schema: day_of_week string, avg_count float
// Hint: You may need to group by the "date" (without time stamp - time in the day) first. Checkout "to_date" function.
// ENTER THE CODE BELOW
val top2daysPU = df_filter.withColumn("date",to_date($"pickup_datetime","yyyy-MM-dd"))
    .withColumn("day_of_week",date_format($"pickup_datetime","EEEE"))
    .withColumn("pickup",lit("1"))
    .groupBy($"day_of_week",$"date")
    .agg(sum($"pickup").as("pickups"))
    .groupBy($"day_of_week").agg(count($"date").as("count_of_date"),sum($"pickups").as("pickups"))
    .withColumn("avg_count",$"pickups"/$"count_of_date")
    .orderBy($"avg_count".desc)
    .drop($"count_of_date")
    .drop($"pickups")

top2daysPU.show(2)

// COMMAND ----------

// PART 5: For each particular hour of a day (0 to 23, 0 being midnight) - in their order from 0 to 23, find the zone in Brooklyn borough with the LARGEST number of pickups. 
// Output Schema: hour_of_day int, zone string, max_count int
// Hint: You may need to use "Window" over hour of day, along with "group by" to find the MAXIMUM count of pickups
// ENTER THE CODE BELOW
val dfhour = df_filter.withColumn("hour_of_day",date_format($"pickup_datetime","H")).withColumn("pickup",lit("1"))
val hourzone = dfhour.select($"PULocationID",$"hour_of_day",$"pickup").join(zones,dfhour("PULocationID") === zones("LocationID"))
    .filter($"Borough" === "Brooklyn")
    .groupBy($"hour_of_day",$"Zone").agg(sum($"pickup").as("pickups"))
    .orderBy($"hour_of_day")

val maxzone = hourzone.groupBy($"hour_of_day".as("hour_of_day2")).agg(max("pickups").as("max_count"))

val tophourzone = hourzone.join(maxzone,(hourzone("pickups") === maxzone("max_count")) && (hourzone("hour_of_day") === maxzone("hour_of_day2")))
    .select($"hour_of_day",$"Zone",$"max_count")
    .withColumn("hour_of_day", col("hour_of_day").cast("Integer"))
    .withColumn("max_count", col("max_count").cast("Integer"))
    .withColumnRenamed("Zone","zone")
    .orderBy($"hour_of_day")

tophourzone.show(5)

// COMMAND ----------

// PART 6 - Find which 3 different days of the January, in Manhattan, saw the largest percentage increment in pickups compared to previous day, in the order from largest increment % to smallest increment %. 
// Print the day of month along with the percent CHANGE (can be negative), rounded to 2 decimal places, in number of pickups compared to previous day.
// Output Schema: day int, percent_change float

// Hint: You might need to use lag function, over a window ordered by day of month.

// ENTER THE CODE BELOW
val dfjan = df_filter.select($"PULocationID",$"pickup_datetime").withColumn("Month",date_format($"pickup_datetime","M"))
    .filter($"Month" === 1).withColumn("pickup",lit("1"))

val Mandfjan = dfjan.join(zones, dfjan("PULocationID") === zones("LocationID")).filter($"Borough" === "Manhattan")
    .withColumn("day",date_format($"pickup_datetime","d"))
    .groupBy($"day").agg(sum($"pickup").as("pickups"))
    .withColumn("day", col("day").cast("Integer"))
    .orderBy($"day")

val window = Window.orderBy("day")
val lagcol = lag(col("pickups"),1).over(window)

val Mandfjan_pct = Mandfjan.withColumn("bf_pickups",lagcol)
    .withColumn("percent_change", ($"pickups" - $"bf_pickups") / $"bf_pickups")
    .withColumn("percent_change", round(col("percent_change"), 2))
    .select($"day",$"percent_change")
    .orderBy($"percent_change".desc)

Mandfjan_pct.show(3)
