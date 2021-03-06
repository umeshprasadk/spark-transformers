package com.flipkart.fdp.ml.adapter;

import com.flipkart.fdp.ml.export.ModelExporter;
import com.flipkart.fdp.ml.importer.ModelImporter;
import com.flipkart.fdp.ml.modelinfo.StringIndexerModelInfo;
import com.flipkart.fdp.ml.transformer.Transformer;
import org.apache.spark.ml.feature.StringIndexer;
import org.apache.spark.ml.feature.StringIndexerModel;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.spark.sql.types.DataTypes.*;
import static org.junit.Assert.assertEquals;

/**
 * Created by akshay.us on 3/2/16.
 */
public class StringIndexerBridgeTest extends SparkTestBase {

    @Test
    public void testStringIndexer() {

        //prepare data
        StructType schema = createStructType(new StructField[]{
                createStructField("id", IntegerType, false),
                createStructField("label", StringType, false)
        });
        List<Row> trainingData = Arrays.asList(
                cr(0, "a"), cr(1, "b"), cr(2, "c"), cr(3, "a"), cr(4, "a"), cr(5, "c"));
        DataFrame dataset = sqlContext.createDataFrame(trainingData, schema);

        //train model in spark
        StringIndexerModel model = new StringIndexer()
                .setInputCol("label")
                .setOutputCol("labelIndex").fit(dataset);

        //Export this model
        byte[] exportedModel = ModelExporter.export(model, dataset);

        //Import and get Transformer
        Transformer transformer = ModelImporter.importAndGetTransformer(exportedModel);

        //compare predictions
        Row[] sparkOutput = model.transform(dataset).orderBy("id").select("id", "label", "labelIndex").collect();
        for (Row row : sparkOutput) {

            Map<String, Object> data = new HashMap<String, Object>();
            data.put(model.getInputCol(), (String) row.get(1));
            transformer.transform(data);
            double indexerOutput = (double) data.get(model.getOutputCol());

            assertEquals(indexerOutput, (double) row.get(2), EPSILON);
        }

    }

    @Test
    public void testStringIndexerForDoubleColumn() {

        //prepare data
        StructType schema = createStructType(new StructField[]{
                createStructField("id", IntegerType, false),
                createStructField("label", DoubleType, false)
        });
        List<Row> trainingData = Arrays.asList(
                cr(0, 1.0), cr(1, 2.0), cr(2, 3.0), cr(3, 1.0), cr(4, 1.0), cr(5, 3.0));
        DataFrame dataset = sqlContext.createDataFrame(trainingData, schema);

        //train model in spark
        StringIndexerModel model = new StringIndexer()
                .setInputCol("label")
                .setOutputCol("labelIndex").fit(dataset);

        //Export this model
        byte[] exportedModel = ModelExporter.export(model, dataset);

        //Import and get Transformer
        Transformer transformer = ModelImporter.importAndGetTransformer(exportedModel);

        //compare predictions
        Row[] sparkOutput = model.transform(dataset).orderBy("id").select("id", "label", "labelIndex").collect();
        for (Row row : sparkOutput) {

            Map<String, Object> data = new HashMap<String, Object>();
            data.put(model.getInputCol(), row.getDouble(1));
            transformer.transform(data);
            double indexerOutput = (double) data.get(model.getOutputCol());
            assertEquals(indexerOutput, row.getDouble(2), EPSILON);
        }

    }

    @Test(expected=RuntimeException.class)
    public void testStringIndexerForUnseenValues() {

        //prepare data
        StructType schema = createStructType(new StructField[]{
                createStructField("id", IntegerType, false),
                createStructField("label", DoubleType, false)
        });
        List<Row> trainingData = Arrays.asList(
                cr(0, 1.0), cr(1, 2.0), cr(2, 3.0), cr(3, 1.0), cr(4, 1.0), cr(5, 3.0));
        DataFrame dataset = sqlContext.createDataFrame(trainingData, schema);

        //train model in spark
        StringIndexerModel model = new StringIndexer()
                .setInputCol("label")
                .setOutputCol("labelIndex").fit(dataset);

        //Export this model
        byte[] exportedModel = ModelExporter.export(model, dataset);

        //Import and get Transformer
        Transformer transformer = ModelImporter.importAndGetTransformer(exportedModel);

        //unseen value
        Map<String, Object> data = new HashMap<String, Object>();
        data.put(model.getInputCol(), 7.0);
        transformer.transform(data);
    }

    @Test
    public void testStringIndexerForHandlingUnseenValues() {

        //prepare data
        StructType schema = createStructType(new StructField[]{
                createStructField("id", IntegerType, false),
                createStructField("label", DoubleType, false)
        });
        List<Row> trainingData = Arrays.asList(
                cr(0, 1.0), cr(1, 2.0), cr(2, 3.0), cr(3, 1.0), cr(4, 1.0), cr(5, 3.0));
        DataFrame dataset = sqlContext.createDataFrame(trainingData, schema);

        //train model in spark
        StringIndexerModel model = new StringIndexer()
                .setInputCol("label")
                .setOutputCol("labelIndex").fit(dataset);

        //Export this model
        byte[] exportedModel = ModelExporter.export(model, dataset);

        StringIndexerModelInfo stringIndexerModelInfo = (StringIndexerModelInfo)ModelImporter.importModelInfo(exportedModel);
        stringIndexerModelInfo.setFailOnUnseenValues(false);

        //Import and get Transformer
        Transformer transformer = stringIndexerModelInfo.getTransformer();

        //unseen value
        Map<String, Object> data = new HashMap<String, Object>();
        data.put(model.getInputCol(), 7.0);
        transformer.transform(data);
        double indexerOutput = (double) data.get(model.getOutputCol());
        assertEquals(indexerOutput, 3.0, EPSILON);

        //unseen value
        data.put(model.getInputCol(), 9.0);
        transformer.transform(data);
        indexerOutput = (double) data.get(model.getOutputCol());
        assertEquals(indexerOutput, 3.0, EPSILON);

        //unseen value
        data.put(model.getInputCol(), 0.0);
        transformer.transform(data);
        indexerOutput = (double) data.get(model.getOutputCol());
        assertEquals(indexerOutput, 3.0, EPSILON);

        //seen value
        data.put(model.getInputCol(), 2.0);
        transformer.transform(data);
        indexerOutput = (double) data.get(model.getOutputCol());
        assertEquals(indexerOutput, stringIndexerModelInfo.getLabelToIndex().get("2.0"), EPSILON);
    }
}
