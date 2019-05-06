package com.hwl.media.remote;

import android.content.Context;

import com.hwl.media.projection.ProjectionManager;
import com.ustc.base.debug.Console;
import com.ustc.base.debug.Console.StreamTunnel;
import com.ustc.base.debug.Dumpper;
import com.ustc.base.util.getopt.Getopt;
import com.ustc.base.util.getopt.LongOpt;

public class ScreenRecord {

    protected static final String TAG = "ScreenRecord";

    public static class Command extends Console.Command {
        
        static final String sUsage = 
                "screencap [-f format]";
        
        static final String sOptString = "f:i:s:";
    
        static final LongOpt[] sLongOptions = new LongOpt[] {
            new LongOpt("format", LongOpt.REQUIRED_ARGUMENT, null, 'f'), 
        };
        private final ProjectionManager mProjectionManager;

        private Context mContext;

        public Command(Context context, ProjectionManager projectionManager) {
            super(sUsage, sOptString, sLongOptions);
            mContext = context;
            mProjectionManager = projectionManager;
        }

    
        @Override
        public  void run(StreamTunnel streamTunnel) throws Exception {
            Getopt opts = getOpts(streamTunnel);
            int c;
            String format = null;
            int id = -1;
            int seg = 5;
            while ((c = opts.getopt()) != -1) {
                switch (c) {
                case 'f':
                    format = opts.getOptarg();
                    break;
                case 'i':
                    id = Integer.valueOf(opts.getOptarg());
                    break;
                case 's':
                    seg = Integer.valueOf(opts.getOptarg());
                    break;
                }
            }
            streamTunnel.setContentType("video/" + format);
            streamTunnel.beginResponse();
            mProjectionManager.startProjection(new StreamTunnel(streamTunnel));
        }
        
        @Override
        public void dump(Dumpper dumpper) {
            super.dump(dumpper);
            //dumpper.dump("mSegmentStream", mSegmentStream);
            //dumpper.dump("mStreamId", mStreamId);
        }

    }

}
