package onlineKafka

import org.apache.spark.ml.classification.MultilayerPerceptronClassifier
import org.apache.spark.ml.feature.PCA
import org.apache.spark.sql.functions._
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types._

import br.ufrj.gta.stream.metrics._
import br.ufrj.gta.stream.schema.flow.Flowtbag
import br.ufrj.gta.stream.util.File

object NeuralNetworkKafka {
    def main(args: Array[String]) {

        // Sets dataset file separation string ("," for csv) and label column name
        val sep = ","
        val labelCol = "label"

        // Sets names for colums created during the algorithm execution, containing PCA and regular features
        val pcaFeaturesCol = "pcaFeatures"
        var featuresCol = "features"

        // Defines dataset schema, dataset csv generated by flowtbag https://github.com/DanielArndt/flowtbag 
        val schema = Flowtbag.getSchema

        // Creates spark session
        val spark = SparkSession.builder.appName("Stream").getOrCreate()

        // Checks arguments
        if (args.length < 9) {
            println("Missing parameters")
            sys.exit(1)
        }

        // Path for training dataset file
        val inputTrainingFile = args(0)

        // Time the program will remain actively monitoring Kafka
        val timeoutStream = args(1).toLong

        // Kafka server address
        val kafkaServer = args(2)

        // Kafka topic name
        val flowsTopic = args(3)

        // Path and filenames for saving progress and classification results
        val outputPath = File.appendSlash(args(4))
        val progressFilename = args(5)
        val metricsFilename = args(6)

        // Sets algorithm hyperparameters
        val layers = args(7).split(',').map(l => l.toInt)
        val maxIter = args(8).toInt

        // Sets total number of PCA features; optional
        val pcaK: Option[Int] = try {
            Some(args(9).toInt)
        } catch {
            case e: Exception => None
        }

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
            .option("subscribe", flowsTopic)
            .load()

        // Creates a single vector column containing all features on the training data
        val featurizedTrainingData = Flowtbag.featurize(inputTrainingData, featuresCol)

        // Receives flow value data from the Kafka topic, and converts it to String
        val valueDataStream = inputTestDataStream
            .select(inputTestDataStream("value").cast("string"))

        // Creates a DataFrame containing the network flow data, adapting the data to fit the Flowtbag format
        val flowsDataStream = valueDataStream
            .withColumn("fields", split(regexp_replace(valueDataStream("value"), "\"", ""), ","))
            .select(Flowtbag.getColsRange.map(c => col("fields").getItem(c).as(s"col$c").cast("int")): _*)
            .toDF(Flowtbag.getColNames: _*)

        // Creates a single vector column containing all features on the test data
        val featurizedTestData = Flowtbag.featurize(flowsDataStream, featuresCol)

        // Applies PCA to training and test data; optional
        val (trainingData, testData) = pcaK match {
            case Some(pcaK) => {
                val pca = new PCA()
                    .setInputCol(featuresCol)
                    .setOutputCol(pcaFeaturesCol)
                    .setK(pcaK)
                    .fit(featurizedTrainingData)

                featuresCol = pcaFeaturesCol

                (pca.transform(featurizedTrainingData), pca.transform(featurizedTestData))
            }
            case None => (featurizedTrainingData, featurizedTestData)
        }

        // Creates a Multilayer Perceptron classifier, using the hyperparameters defined previously
        val classifier = new MultilayerPerceptronClassifier()
            .setFeaturesCol(featuresCol)
            .setLabelCol(labelCol)
            .setLayers(layers)
            .setMaxIter(maxIter)

        // Fits the training data to the classifier, creating the classification model
        val model = classifier.fit(trainingData)

        // Tests model on the test data
        val prediction = model.transform(testData)

        // Creates a column with the classification prediction
        val predictionCol = classifier.getPredictionCol

        // Starts collecting streaming metrics (including "Input rows per second" and "Processed rows per second")
        val streamingMetrics = new StreamingMetrics(StreamingMetrics.names)
        spark.streams.addListener(streamingMetrics.getListener)

        // Write the classification results on the output path
        val outputDataStream = prediction.select(prediction(labelCol), prediction(predictionCol)).writeStream
            .outputMode("append")
            .option("checkpointLocation", outputPath + "checkpoints/")
            .format("csv")
            .option("path", outputPath)
            .start()

        // Wait until timeout to stop the online classification tool
        outputDataStream.awaitTermination(timeoutStream)

        // Reads the classification results and calculates the prediction metrics (including "Accuracy", "Precision", "Recall" and "F1-score")
        val metrics = new PredictionMetrics(PredictionMetrics.names)
        val inputResultData = spark.read
            .option("sep", sep)
            .option("header", false)
            .schema(new StructType().add(labelCol, "integer").add(predictionCol, "double"))
            .csv(outputPath + "*.csv")
        metrics.add(metrics.getMetrics(inputResultData, labelCol, predictionCol))

        // Saves streaming and prediction metrics on a csv file
        metrics.export(metricsFilename, Metrics.FormatCsv)
        streamingMetrics.export(progressFilename, Metrics.FormatCsv)

        spark.stop()
    }
}
