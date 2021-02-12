package offline

import org.apache.spark.ml.feature.PCA
import org.apache.spark.sql.functions._
import org.apache.spark.sql.SparkSession

import br.ufrj.gta.stream.metrics._
import br.ufrj.gta.stream.ml.classification.anomaly.MeanVarianceClassifier
import org.apache.spark.ml.evaluation.{MulticlassClassificationEvaluator, BinaryClassificationEvaluator}
import br.ufrj.gta.stream.schema.flow.Flowtbag
import br.ufrj.gta.stream.util.File
import org.apache.spark.sql.types._
import org.apache.spark.sql.Row

import org.elasticsearch.spark._
import org.elasticsearch.spark.sql._
import org.elasticsearch.hadoop.cfg.ConfigurationOptions

object MeanVariance {
    def main(args: Array[String]) {
/*
        // Sets dataset file separation string ("," for csv) and label column name
        val sep = ","
        val labelCol = "label"

        // Sets names for colums created during the algorithm execution, containing PCA and regular features
        val pcaFeaturesCol = "pcaFeatures"
        var featuresCol = "features"

        // Defines dataset schema, dataset csv generated by flowtbag https://github.com/DanielArndt/flowtbag
        val schema = Flowtbag.getSchema

        // Checks arguments
        if (args.length < 3) {
            println("Missing parameters")
            sys.exit(1)
        }

        // Path for training dataset file (assumes all entries to contain legitimate traffic)
        val inputTrainingFile = args(0)

        // Path for test dataset file
        val inputTestFile = args(1)

        // String cointaining all slave nodes; defaults to localhost if empty
        val slaveNodes = if (args(2) != "local") args(1) else "localhost"

        // Dataset used for model creation and test
        val dataset = args(3)

        // Sets algorithm hyperparameters
        //val threshold = args(5).toDouble
        val threshold = 1.0

        // Creates spark session
        val spark = SparkSession
            .builder
            .config(ConfigurationOptions.ES_NODES, slaveNodes)
            .config(ConfigurationOptions.ES_RESOURCE, "spark-offline/classification")
            .appName("Stream")
            .getOrCreate()

        // Reads the training csv dataset file, fitting it to the schema
        val inputTrainingData = spark.read
            .option("sep", sep)
            .option("header", false)
            .schema(schema)
            .csv(inputTrainingFile)

        // Reads the test csv dataset file, fitting it to the schema
        val inputTestData = spark.read
            .option("sep", sep)
            .option("header", false)
            .schema(schema)
            .csv(inputTestFile)

        // Creates a single vector column containing all features on training and test data
        val featurizedTrainingData = Flowtbag.featurize(inputTrainingData, featuresCol)
        val featurizedTestData = Flowtbag.featurize(inputTestData, featuresCol)

        // Splits the training dataset on two subsections; larger section (70%) is selected as new training dataset
        val splitData = Array(featurizedTrainingData.randomSplit(Array(0.7, 0.3))(0), featurizedTestData.randomSplit(Array(0.7, 0.3))(1))
        val trainingData = splitData(0)
        val testData = splitData(1)

        var startTime = System.currentTimeMillis()

        // Creates a Mean-Variance classifier, using the hyperparameters defined previously
        val classifier = new MeanVarianceClassifier()
            .setFeaturesCol(featuresCol)
            .setLabelCol(labelCol)
            .setThreshold(threshold)

        // Fits the training data to the classifier, creating the classification model
        val model = classifier.fit(trainingData)

        val trainingTime = (System.currentTimeMillis() - startTime) / 1000.0

        startTime = System.currentTimeMillis()

        // Tests model on the test data
        val prediction = model.transform(testData)

        // Creates a column with the classification prediction
        val predictionCol = classifier.getPredictionCol

        // Cache model to improve performance
        prediction.cache()

        // Perform an action to accurately measure the test time
        prediction.count()

        val testTime = (System.currentTimeMillis() - startTime) / 1000.0

        // Removes model from cache
        prediction.unpersist()

        // Compute evaluation metrics
        val f1Evaluator = new MulticlassClassificationEvaluator().setMetricName("f1")
        val weightedPrecisionEvaluator = new MulticlassClassificationEvaluator().setMetricName("weightedPrecision")
        val weightedRecallEvaluator = new MulticlassClassificationEvaluator().setMetricName("weightedRecall")
        val accuracyEvaluator = new MulticlassClassificationEvaluator().setMetricName("accuracy")
        val aucEvaluator = new BinaryClassificationEvaluator().setMetricName("areaUnderROC")

        val f1 = f1Evaluator.evaluate(prediction)
        val weightedPrecision = weightedPrecisionEvaluator.evaluate(prediction)
        val weightedRecall = weightedRecallEvaluator.evaluate(prediction)
        val accuracy = accuracyEvaluator.evaluate(prediction)
        val auc = aucEvaluator.evaluate(prediction)

        // Creates a DataFrame with the resulting metrics, and send them to ElasticSearch
        val elasticDF = Seq(Row("Mean-Variance", accuracy, weightedPrecision, weightedRecall, f1, auc, trainingTime, dataset))
        val elasticSchema = List(
          StructField("algorithm", StringType, true),
          StructField("accuracy", DoubleType, true),
          StructField("precision", DoubleType, true),
          StructField("recall", DoubleType, true),
          StructField("f1-score", DoubleType, true),
          StructField("auc", DoubleType, true),
          StructField("training time", DoubleType, true),
          StructField("dataset", StringType, true))
        val someDF = spark
            .createDataFrame(spark.sparkContext.parallelize(elasticDF),StructType(elasticSchema))
            .saveToEs("spark-offline/classification")

        spark.stop()
*/
    }
}