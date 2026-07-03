package com.showengine.service;

import com.showengine.config.ShowEngineProperties;
import com.showengine.router.enums.IntentSourceEnum;
import com.showengine.router.enums.IntentTypeEnum;
import com.showengine.model.AskRequest;
import com.showengine.router.model.IntentResult;
import com.showengine.router.service.impl.HybridIntentRouterImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests ModelIntentRouterImpl utility methods and fallback behavior.
 * Does not load a real ONNX model because the test environment has no model files.
 */
class ModelIntentRouterTest {

    private HybridIntentRouterImpl.ModelIntentRouterImpl router;

    @BeforeEach
    void setUp() {
        ShowEngineProperties props = new ShowEngineProperties();
        ShowEngineProperties.Classifier cfg = new ShowEngineProperties.Classifier();
        cfg.setEnabled(false); // Disable it so model files are not loaded.
        props.setClassifier(cfg);
        router = new HybridIntentRouterImpl.ModelIntentRouterImpl(props);
        // Do not call init(); this simulates the model-not-ready state.
    }

    @Test
    void routeWhenNotReady_returnsUnknownWithZeroConfidence() {
        AskRequest req = new AskRequest();
        req.setQuestion("帮我写段代码");
        IntentResult result = router.route(req);
        assertThat(result.getIntentType()).isEqualTo(IntentTypeEnum.UNKNOWN);
        assertThat(result.getConfidence()).isEqualTo(0.0);
        assertThat(result.getSource()).isEqualTo(IntentSourceEnum.MODEL);
    }

    @Test
    void l2Normalize_zeroVector_returnsUnchanged() throws Exception {
        Method m = HybridIntentRouterImpl.ModelIntentRouterImpl.class.getDeclaredMethod("l2Normalize", float[].class);
        m.setAccessible(true);
        float[] zero = new float[]{0, 0, 0};
        float[] result = (float[]) m.invoke(router, zero);
        assertThat(result).containsExactly(0f, 0f, 0f);
    }

    @Test
    void l2Normalize_unitVector_unchanged() throws Exception {
        Method m = HybridIntentRouterImpl.ModelIntentRouterImpl.class.getDeclaredMethod("l2Normalize", float[].class);
        m.setAccessible(true);
        // [1,0,0] is already a unit vector.
        float[] v = new float[]{1f, 0f, 0f};
        float[] result = (float[]) m.invoke(router, v);
        assertThat(result[0]).isCloseTo(1.0f, within(1e-6f));
    }

    @Test
    void dotProduct_orthogonalVectors_isZero() throws Exception {
        Method m = HybridIntentRouterImpl.ModelIntentRouterImpl.class.getDeclaredMethod("dotProduct", float[].class, float[].class);
        m.setAccessible(true);
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        double result = (double) m.invoke(router, a, b);
        assertThat(result).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void dotProduct_sameUnitVector_isOne() throws Exception {
        Method m = HybridIntentRouterImpl.ModelIntentRouterImpl.class.getDeclaredMethod("dotProduct", float[].class, float[].class);
        m.setAccessible(true);
        float[] a = {1f, 0f, 0f};
        double result = (double) m.invoke(router, a, a);
        assertThat(result).isCloseTo(1.0, within(1e-9));
    }
}
