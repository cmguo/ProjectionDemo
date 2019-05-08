package com.hwl.media.projection;

import android.opengl.GLES20;

import com.ustc.base.debug.Dumpable;
import com.ustc.base.debug.Dumpper;

import jp.co.cyberagent.android.gpuimage.GLParam;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageMixBlendFilter;

public class GPUImageDisplayFilter extends GPUImageMixBlendFilter implements Dumpable {

    public static final String DISPLAY_FRAGMENT_SHADER =
            " varying highp vec2 textureCoordinate;\n" +
                    " varying highp vec2 textureCoordinate2;\n" +
                    "\n" +
                    " uniform sampler2D inputImageTexture;\n" +
                    " uniform sampler2D inputImageTexture2;\n" +
                    " uniform mediump vec4 bound;\n" +
                    " uniform mediump float mixturePercent;\n" +
                    " \n" +
                    " void main()\n" +
                    " {\n" +
                    "    mediump vec4 color = texture2D(inputImageTexture, textureCoordinate * bound.zw + bound.xy);\n" +
                    "    mediump vec4 color2 = texture2D(inputImageTexture2, textureCoordinate);\n" +
                    "    gl_FragColor = vec4(mix(color.rgb, color2.rgb, mixturePercent), color.a);\n" +
                    " }\n";

    private float[] bound = new float[] {0.0f, 0.0f, 1f, 1f};

    private int boundLocation;

    public GPUImageDisplayFilter() {
        super(DISPLAY_FRAGMENT_SHADER, 0.5f);
    }

    @Override
    public void onInit() {
        super.onInit();
        boundLocation = GLES20.glGetUniformLocation(getProgram(), "bound");
    }

    @Override
    public void onInitialized() {
        super.onInitialized();
        setBound(bound);
    }


    @GLParam(min=0, max=1, dflt=0.1f)
    public void setBound(final float[] bound) {
        this.bound = bound;
        setFloatVec4(boundLocation, this.bound);
    }

    @Override
    public void dump(Dumpper dumpper) {
        dumpper.dump("bound", bound);
    }
    
}
