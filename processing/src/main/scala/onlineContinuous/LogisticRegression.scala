package onlineContinuous

import org.apache.spark.ml.classification.{LogisticRegression => LogisticRegressionClassifier}
import org.apache.spark.ml.feature.PCA
import org.apache.spark.sql.functions._
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types._
import org.apache.spark.sql.streaming.Trigger

import br.ufrj.gta.stream.metrics._
import br.ufrj.gta.stream.schema.flow.Flowtbag
import br.ufrj.gta.stream.util.File

import org.elasticsearch.spark._
import org.elasticsearch.spark.sql._
import org.elasticsearch.hadoop.cfg.ConfigurationOptions

object LogisticRegressionContinuous {
    def main(args: Array[String]) {

        // Sets dataset file separation string ("," for csv) and label column name
        val sep = ","
        val labelCol = "label"

        // Sets names for the colum created containing regular features
        var featuresCol = "features"

        // Defines dataset schema, dataset csv generated by flowtbag https://github.com/DanielArndt/flowtbag
        val schema = Flowtbag.getSchema

        // Checks arguments
        if (args.length < 4) {
            println("Missing parameters")
            sys.exit(1)
        }

        // Path for training dataset file
        val inputTrainingFile = args(0)

        // Kafka server address
        val kafkaServer = args(1)

        // String cointaining all slave nodes; defaults to localhost if empty
        val slaveNodes = if (args(2) != "") args(2) else "localhost"

        // Kafka topic for Kafka checkpoints
        val kafkaCheckpoint = File.appendSlash(args(3))

        // Kafka topic for Elastic checkpoints
        val elasticCheckpoint = File.appendSlash(args(4))

        // Sets algorithm hyperparameters
        val regParam = 0.03
        val elasticNetParam = 0.8
        val maxIter = 20

        // Creates spark session
        val spark = SparkSession
            .builder
            .config(ConfigurationOptions.ES_NODES, slaveNodes)
            //.config(ConfigurationOptions.ES_PORT, "9200")
            .config(ConfigurationOptions.ES_RESOURCE, "spark/classification")
            .config("spark.streaming.receiver.maxRate", 100)
            .appName("Stream")
            .getOrCreate()

        // Reads csv dataset file, fitting it to the schema
        val inputTrainingData = spark.read
            .option("sep", sep)
            .option("header", false)
            .schema(schema)
            .csv(inputTrainingFile)

        // Reads new data arriving at the Kafka topic
        val inputTestDataStream = spark.readStream
            .format("kafka")
            .option("kafka.bootstrap.servers", kafkaServer)
            .option("subscribe", "flow_abstraction")
            .load()

        // Creates a single vector column containing all features on the training data
        val trainingData = Flowtbag.featurize(inputTrainingData, featuresCol)

        // Receives flow value data from the Kafka topic, and converts it to String
        val valueDataStream = inputTestDataStream
            .select(inputTestDataStream("value").cast("string"))

        // Creates a DataFrame containing the network flow data, adapting the data to fit the Flowtbag format
        val flowsDataStream = valueDataStream
            .withColumn("fields", split(regexp_replace(valueDataStream("value"), "\"", ""), ","))
            .select(Flowtbag.getColsRange.map(c => col("fields").getItem(c).as(s"col$c")): _*)
            .toDF(Flowtbag.getColNames: _*)
            .withColumn("srcport", col("srcport").cast("int"))
            .withColumn("dstport", col("dstport").cast("int"))
            .withColumn("proto", col("proto").cast("int"))
            .withColumn("total_fpackets", col("total_fpackets").cast("int"))
            .withColumn("total_fvolume", col("total_fvolume").cast("int"))
            .withColumn("total_bpackets", col("total_bpackets").cast("int"))
            .withColumn("total_bvolume", col("total_bvolume").cast("int"))
            .withColumn("min_fpktl", col("min_fpktl").cast("int"))
            .withColumn("mean_fpktl", col("mean_fpktl").cast("int"))
            .withColumn("max_fpktl", col("max_fpktl").cast("int"))
            .withColumn("std_fpktl", col("std_fpktl").cast("int"))
            .withColumn("min_bpktl", col("min_bpktl").cast("int"))
            .withColumn("mean_bpktl", col("mean_bpktl").cast("int"))
            .withColumn("max_bpktl", col("max_bpktl").cast("int"))
            .withColumn("std_bpktl", col("std_bpktl").cast("int"))
            .withColumn("min_fiat", col("min_fiat").cast("int"))
            .withColumn("mean_fiat", col("mean_fiat").cast("int"))
            .withColumn("max_fiat", col("max_fiat").cast("int"))
            .withColumn("std_fiat", col("std_fiat").cast("int"))
            .withColumn("min_biat", col("min_biat").cast("int"))
            .withColumn("mean_biat", col("mean_biat").cast("int"))
            .withColumn("max_biat", col("max_biat").cast("int"))
            .withColumn("std_biat", col("std_biat").cast("int"))
            .withColumn("duration", col("duration").cast("int"))
            .withColumn("min_active", col("min_active").cast("int"))
            .withColumn("mean_active", col("mean_active").cast("int"))
            .withColumn("max_active", col("max_active").cast("int"))
            .withColumn("std_active", col("std_active").cast("int"))
            .withColumn("min_idle", col("min_idle").cast("int"))
            .withColumn("mean_idle", col("mean_idle").cast("int"))
            .withColumn("max_idle", col("max_idle").cast("int"))
            .withColumn("std_idle", col("std_idle").cast("int"))
            .withColumn("sflow_fpackets", col("sflow_fpackets").cast("int"))
            .withColumn("sflow_fbytes", col("sflow_fbytes").cast("int"))
            .withColumn("sflow_bpackets", col("sflow_bpackets").cast("int"))
            .withColumn("sflow_bbytes", col("sflow_bbytes").cast("int"))
            .withColumn("fpsh_cnt", col("fpsh_cnt").cast("int"))
            .withColumn("bpsh_cnt", col("bpsh_cnt").cast("int"))
            .withColumn("furg_cnt", col("furg_cnt").cast("int"))
            .withColumn("burg_cnt", col("burg_cnt").cast("int"))
            .withColumn("total_fhlen", col("total_fhlen").cast("int"))
            .withColumn("total_bhlen", col("total_bhlen").cast("int"))
            .withColumn("dscp", col("dscp").cast("int"))
            .withColumn("label", col("label").cast("int"))

        // Creates a single vector column containing all features on the test data
        val testData = Flowtbag.featurize(flowsDataStream, featuresCol)

        // Creates a Logistic Regression classifier, using the hyperparameters defined previously
        val classifier = new LogisticRegressionClassifier()
            .setFeaturesCol(featuresCol)
            .setLabelCol(labelCol)
            .setRegParam(regParam)
            .setElasticNetParam(elasticNetParam)
            .setMaxIter(maxIter)

        // Fits the training data to the classifier, creating the classification model
        val model = classifier.fit(trainingData)

        def current_time = udf(() => {
            java.time.LocalDateTime.now().toString
        })

        // Tests model on the test data
        val prediction = model
            .transform(testData)
            .withColumn("date", current_time())

        // Creates a column with the classification prediction
        val predictionCol = classifier.getPredictionCol

        // Write the classification results on Kafka
        val outputDataStream = prediction
            .select(to_json(struct(prediction("srcip"), prediction("srcport"), prediction("dstip"), prediction("dstport"), prediction("proto"), prediction("date"), prediction(predictionCol))).alias("value"))
            .writeStream
            .format("kafka")
            .option("kafka.bootstrap.servers", kafkaServer)
            .option("topic", "flow_classification")
            .option("checkpointLocation", kafkaCheckpoint)
            .trigger(Trigger.Continuous(1000))
            .start()

        // Write the classification results on Elasticsearch
        val outputElastic = spark
            .readStream
            .format("kafka")
            .option("kafka.bootstrap.servers", kafkaServer)
            .option("subscribe", "flow_classification")
            .load()
            .selectExpr("CAST(value AS STRING)")
            .writeStream
            .format("es")
            .option("checkpointLocation", elasticCheckpoint)
            .start("spark/classification")

        // Wait until timeout to stop the online classification tool
        outputDataStream.awaitTermination()
        outputElastic.awaitTermination()

        spark.stop()
    }
}