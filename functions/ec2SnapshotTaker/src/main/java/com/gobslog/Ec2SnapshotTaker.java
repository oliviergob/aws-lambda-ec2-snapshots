package com.gobslog;

import com.amazonaws.services.lambda.runtime.Context;
import java.util.Map;

public class Ec2SnapshotTaker {
    public void lambdaHandler(Map<String,Object> input, Context context) {
        System.out.println("Hello Lambda" + input.toString());
    }
}
