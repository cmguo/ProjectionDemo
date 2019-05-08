package com.hwl.media.projection;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import com.ustc.base.debug.Dumpable;
import com.ustc.base.debug.Dumpper;
import com.ustc.base.debug.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class EncodeStream implements Dumpable{

	private static final String TAG = "EncodeStream";

    private MediaCodec mEncoder;
	private MediaCodec.BufferInfo mBufferInfo;
    private Surface mSurface;
    private Thread mEncodeThread;
    private Context mContext;

    public EncodeStream(Context context) {
    	this.mContext=context;
    }


    public void init(int width, int heigth) {
        MediaFormat format = null;
		//MediaCodecInfo codec = find_codec("video/avc");
		//MediaCodecInfo.CodecCapabilities capabilities = codec.getCapabilitiesForType("video/avc");
		//int[] clr_fmts = capabilities.colorFormats;
		float frameRate = 20f;
		int bitrate =
				width * heigth * (int) frameRate / 8;
		format = MediaFormat.createVideoFormat("video/avc", width, heigth);
		format.setFloat(MediaFormat.KEY_FRAME_RATE, frameRate);
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT, // TODO: from mine type
				MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
		format.setInteger(MediaFormat.KEY_LATENCY, 0);
		try {
			//mEncoder = MediaCodec.createEncoderByType("video/avc");

			mEncoder = MediaCodec.createByCodecName(EncoderDebugger.debug(mContext,1024,768).getEncoderName());
			mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		} catch (IOException e) {
			Log.d(TAG,e.toString());
		}
	}

    public Surface getInputSurface() {
        mSurface = mEncoder.createInputSurface();
        return mSurface;
    }

    @SuppressWarnings("deprecation")
	public void start() {
	    mEncoder.start();
	    mBufferInfo = new MediaCodec.BufferInfo();
	    if (mSurface != null) {
	        final long startTime = System.nanoTime() / 1000;
	        return;
	    }
	}
    
	protected void bumpSamples(long startTime,OutputStream ops ) throws IOException {
        final long timeScale = 1000000;
        int numTotal = 0;
        long nextTime = 5 * timeScale;
        Log.d(TAG, "bumpSamples startTime " + startTime);
		WritableByteChannel c = Channels.newChannel(ops);
	    while (!Thread.interrupted()) {
	        long time = System.nanoTime() / 1000 - startTime;
            if (time >= nextTime) {
                nextTime += 5 * timeScale;
            }
            if (popSample(startTime, 100,c)) {
				ops.flush();
				++numTotal;
			}
	    }
		Log.d(TAG, "bumpSamples stopped");
    }

	protected boolean popSample(long startTime, int timeout, WritableByteChannel channel) throws IOException {
		int index = mEncoder.dequeueOutputBuffer(mBufferInfo, timeout * 1000);
		if (index >= 0) {
		    ByteBuffer bytes = mEncoder.getOutputBuffer(index);

            if (mBufferInfo.flags != 0) {
				if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
					Log.d(TAG, "Codec Config "
							+ " size: " + mBufferInfo.size
							+ " time: " + mBufferInfo.presentationTimeUs);
					channel.write(bytes);
					mEncoder.releaseOutputBuffer(index, false);
					return false;
				}
			}

			//channel.(bytes.array());
            channel.write(bytes);
			mEncoder.releaseOutputBuffer(index, false);
			return true;
		} else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
		} else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
			Log.d(TAG, "Output Format " + mEncoder.getOutputFormat());
		}
		return false;
	}

	public void stop() {
	    if (mEncodeThread != null) {
            mEncodeThread.interrupt();
            try {
                mEncodeThread.join();
            } catch (InterruptedException e) {
            }
            mSurface.release();
        }
	    mEncoder.stop();
	}

	public void term() {
	    if (mEncoder != null)
	        mEncoder.release();
	}

	@Override
	public void dump(Dumpper dumpper) {
        dumpper.dump("mEncoder", mEncoder);
        dumpper.dump("mBufferInfo", mBufferInfo);
	}
	
	@SuppressWarnings("deprecation")
    public static MediaCodecInfo findCodec(String mineType) {
		for (int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
			MediaCodecInfo codec = MediaCodecList.getCodecInfoAt(i);
			if (!codec.isEncoder()) {
				continue;
			}
			if (!codec.getName().startsWith("OMX.")) {
				continue;
			}
			String[] types = codec.getSupportedTypes();
			for (int j = 0; j < types.length; ++j) {
				Log.d(TAG, "Found codec " + codec.getName() + ", support type " + types[j]);
				if (types[j].equalsIgnoreCase(mineType)) {
					Log.d(TAG, "Found codec for mine type " + mineType + " -> " + codec.getName());
					return codec;
				}
			}
		}
		Log.d(TAG, "Not Found codec for mine type " + mineType);
		return null;
	}

}
