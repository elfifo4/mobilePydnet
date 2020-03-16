package unibo.cvlab.pydnet;

import android.app.Application;
import android.support.annotation.NonNull;

import timber.log.Timber;

/**
 * Created by Elad on 3/5/20.
 */
public class PydnetApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree() {
                             @Override
                             protected String createStackElementTag(@NonNull StackTraceElement element) {
                                 return String.format("(%s:%s)[%s]",
                                         element.getFileName(),
                                         element.getLineNumber(),
                                         element.getMethodName());
                             }
                         }
            );
        }
    }
}
