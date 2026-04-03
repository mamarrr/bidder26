import java.io.BufferedReader;
import java.io.InputStreamReader;

import bid.BidCalculator;

import runner.Runner;

public class Main {
    public static void main(String[] args) {
        if (args.length == 1 ) {
            try {
                int startingEBucks = Integer.parseInt(args[0]);
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                new Runner(startingEBucks, reader);
            } catch (NumberFormatException e) {
                System.err.println("Invalid argument for entrypoint, terminating, err: " + e);
            }
        }
        
        
    }
}
