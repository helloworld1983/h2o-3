package ai.h2o.targetencoding.strategy;

import ai.h2o.targetencoding.TargetEncoderModel;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria;
import hex.grid.HyperSpaceWalker;
import hex.grid.filter.PermutationFilterFunction;
import hex.grid.filter.KeepOnlyFirstMatchFilterFunction;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RGSWithFilteringTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void randomGridSearchWithFunctionsToFilterOutUnnecessaryGridItems() {
    HashMap<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("_blending", new Boolean[]{true, false});
    hpGrid.put("_noise_level", new Double[]{0.0, 0.01, 0.1});
    hpGrid.put("_k", new Double[]{1.0, 2.0, 3.0});
    hpGrid.put("_f", new Double[]{1.0, 2.0, 3.0});

    TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();

    GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();


    KeepOnlyFirstMatchFilterFunction filterFunction1 = new KeepOnlyFirstMatchFilterFunction((gridItem) -> {
      Object mainHP = gridItem.get("_blending");
      return mainHP instanceof Boolean && !(Boolean) mainHP; // TODO handle cases when type is not that expected
    });

    PermutationFilterFunction filterFunction2 = gridItem -> {
      return !((double) gridItem.get("_k") == 3.0 && (double) gridItem.get("_f") == 1.0);
    };

    ArrayList<PermutationFilterFunction> filterFunctions = new ArrayList<>();
    filterFunctions.add(filterFunction1);
    filterFunctions.add(filterFunction2);

    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker =
            new HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters>(parameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria,
                    filterFunctions);

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = walker.iterator();

    ArrayList<TargetEncoderModel.TargetEncoderParameters> evaluatedGridItems = new ArrayList<>();
    while (iterator.hasNext(null)) {
      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = iterator.nextModelParameters(null);
      if( targetEncoderParameters != null) { // we might have had next element but it can be filtered out by ffiltering unctions
        evaluatedGridItems.add(targetEncoderParameters);
      }
    }
    System.out.println("\nAll grid items after applying filtering functions:");
    evaluatedGridItems.forEach(System.out::println);

    // Expected number of the grid items is a result of filtering out permutations with two filtering functions 
    // and taking into account that there are intersections of these functions 
    //
    //       total    filtered by filterFunction1 except first match          filtered by filterFunction2       overlap
    //  25 = 54     - (27 - 1)                                                - 6                               + 3 

    assertEquals(25, evaluatedGridItems.size());
  }

  @Test
  public void randomGridSearchWithFilteringFunctionsWhenMaxModelsIsDefined() {
    HashMap<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("_blending", new Boolean[]{true, false});
    hpGrid.put("_noise_level", new Double[]{0.0, 0.01, 0.1});
    hpGrid.put("_k", new Double[]{1.0, 2.0, 3.0});
    hpGrid.put("_f", new Double[]{1.0, 2.0, 3.0});

    TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();

    GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
    hyperSpaceSearchCriteria.set_max_models(10);

    KeepOnlyFirstMatchFilterFunction blendingFilterFunction = new KeepOnlyFirstMatchFilterFunction((gridItem) -> {
      Object mainHP = gridItem.get("_blending");
      return mainHP instanceof Boolean && !(Boolean) mainHP;
    });

    ArrayList<PermutationFilterFunction> filterFunctions = new ArrayList<>();
    filterFunctions.add(blendingFilterFunction);

    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker =
            new HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters>(parameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria,
                    filterFunctions);

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = walker.iterator();

    ArrayList<TargetEncoderModel.TargetEncoderParameters> evaluatedGridItems = new ArrayList<>();
    while (iterator.hasNext(null)) {
      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = iterator.nextModelParameters(null);
      if( targetEncoderParameters != null) { // we might have had next element but it can be filtered out by ffiltering unctions
        evaluatedGridItems.add(targetEncoderParameters);
      }
    }
    System.out.println("\nAll grid items after applying filtering functions:");
    evaluatedGridItems.forEach(System.out::println);

    assertEquals(10, evaluatedGridItems.size());
  }

  @Test
  public void max_model_is_honored_when_one_model_has_failed() {
    HashMap<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("_blending", new Boolean[]{true, false});
    hpGrid.put("_noise_level", new Double[]{0.0, 0.01, 0.1});
    hpGrid.put("_k", new Double[]{1.0, 2.0, 3.0});
    hpGrid.put("_f", new Double[]{1.0, 2.0, 3.0});

    TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();

    GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
    int maxModels = 10;
    int numberOfFailedModels = 1;
    hyperSpaceSearchCriteria.set_max_models(maxModels);

    KeepOnlyFirstMatchFilterFunction blendingFilterFunction = new KeepOnlyFirstMatchFilterFunction((gridItem) -> {
      Object mainHP = gridItem.get("_blending");
      return mainHP instanceof Boolean && !(Boolean) mainHP;
    });

    ArrayList<PermutationFilterFunction> filterFunctions = new ArrayList<>();
    filterFunctions.add(blendingFilterFunction);

    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker =
            new HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters>(parameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria,
                    filterFunctions);

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = walker.iterator();

    ArrayList<TargetEncoderModel.TargetEncoderParameters> returnedGridItems = new ArrayList<>();
    while (iterator.hasNext(null)) {
      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = iterator.nextModelParameters(null);
      
      // For the sake of test let's pretend that 3rd parameters lead to model's failure. 
      // By notifying `iterator` we will be able to reach `max_model` number of models
      if(returnedGridItems.size() == 3) {
        iterator.modelFailed(null);
      }
      if( targetEncoderParameters != null) { // we might have had next element but it can be filtered out by ffiltering unctions
        returnedGridItems.add(targetEncoderParameters);
      }
    }

    assertEquals(maxModels + numberOfFailedModels, returnedGridItems.size());
  }

  @Test
  public void rgs_reset_is_working() {
    HashMap<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("_blending", new Boolean[]{false});
    hpGrid.put("_noise_level", new Double[]{0.0, 0.01, 0.1});
    hpGrid.put("_k", new Double[]{1.0});
    hpGrid.put("_f", new Double[]{3.0});

    TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();

    GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();

    KeepOnlyFirstMatchFilterFunction blendingFilterFunction = new KeepOnlyFirstMatchFilterFunction((gridItem) -> {
      Object mainHP = gridItem.get("_blending");
      return mainHP instanceof Boolean && !(Boolean) mainHP;
    });

    ArrayList<PermutationFilterFunction> filterFunctions = new ArrayList<>();
    filterFunctions.add(blendingFilterFunction);

    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker =
            new HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters>(parameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria,
                    filterFunctions);

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = walker.iterator();

    ArrayList<TargetEncoderModel.TargetEncoderParameters> returnedGridItems = new ArrayList<>();
    boolean wasResetOnce = false;
    while (iterator.hasNext(null)) {
      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = iterator.nextModelParameters(null);

      if(!wasResetOnce && returnedGridItems.size() == 1) {
        iterator.reset();
        wasResetOnce = true;
      } else {
        if (targetEncoderParameters != null) { // we might have had next element but it can be filtered out by ffiltering unctions
          returnedGridItems.add(targetEncoderParameters);
        }
      }
    }

    // Without resetting iterator would return only one grid item for evaluation. With one reset - we get two returned grid items correspondingly.
    assertEquals(2, returnedGridItems.size());
  }
  
  @Test
  public void randomGridSearchWithFunctionsToFilterOutUnnecessaryGridItems_GBMParametersGrid() {
    Map<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("_ntrees", new Integer[]{10000});
    hpGrid.put("_max_depth", new Integer[]{3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17}); 
    hpGrid.put("_min_rows", new Integer[]{1, 5, 10, 15, 30, 100}); 
    hpGrid.put("_learn_rate", new Double[]{0.001, 0.005, 0.008, 0.01, 0.05, 0.08, 0.1, 0.5, 0.8}); 
    hpGrid.put("_sample_rate", new Double[]{0.50, 0.60, 0.70, 0.80, 0.90, 1.00}); 
    hpGrid.put("_col_sample_rate", new Double[]{ 0.4, 0.7, 1.0}); 
    hpGrid.put("_col_sample_rate_per_tree", new Double[]{ 0.4, 0.7, 1.0}); 
    hpGrid.put("_min_split_improvement", new Double[]{1e-4, 1e-5});

    int totalNumberOfPermutationsInHPGrid = 87480;

    GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();

    GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();

    PermutationFilterFunction strictFilterFunction = gridItem -> {
      return !((int) gridItem.get("_max_depth") >= 15.0 && (int) gridItem.get("_min_rows") >= 50);
    };
    int expectedNumberOfFilteredOutPermutations = 2916;

    ArrayList<PermutationFilterFunction> filterFunctions = new ArrayList<>();
    filterFunctions.add(strictFilterFunction);

    HyperSpaceWalker.RandomDiscreteValueWalker<GBMModel.GBMParameters> walker =
            new HyperSpaceWalker.RandomDiscreteValueWalker<>(gbmParameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria,
                    filterFunctions);

    HyperSpaceWalker.HyperSpaceIterator<GBMModel.GBMParameters> iterator = walker.iterator();

    ArrayList<GBMModel.GBMParameters> filteredGridItems = new ArrayList<>();
    while (iterator.hasNext(null)) {
      GBMModel.GBMParameters gbmParams = iterator.nextModelParameters(null);
      if( gbmParams != null) { // we might have had next element ( iterator.hasNext) = true) but it could be filtered out by filtering functions
        filteredGridItems.add(gbmParams);
      }
    }

    assertEquals(totalNumberOfPermutationsInHPGrid - expectedNumberOfFilteredOutPermutations, filteredGridItems.size());
  }

  @Test
  public void test_class_KeepOnlyFirstMatchFilterFunction_was_extended_properly() {
    KeepOnlyFirstMatchFilterFunction ff1 = new KeepOnlyFirstMatchFilterFunction((gridItem) -> {
      Object mainHP = gridItem.get("_blending");
      return mainHP instanceof Boolean && !(Boolean) mainHP;
    });

    KeepOnlyFirstMatchFilterFunction ff2 = new KeepOnlyFirstMatchFilterFunction((gridItem) -> {
      Object mainHP = gridItem.get("_noise_level");
      return mainHP instanceof Double && (Double) mainHP == 0.01;
    });

    Map<String, Object> permutation = new HashMap<>();
    permutation.put("_blending", false);
    permutation.put("_noise_level",  0.01);
    
    assertTrue(ff1.apply(permutation));
    assertTrue(ff2.apply(permutation));
  }
}
