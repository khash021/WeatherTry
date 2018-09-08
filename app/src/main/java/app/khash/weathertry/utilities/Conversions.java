package app.khash.weathertry.utilities;

import android.text.TextUtils;
import android.util.Log;

public class Conversions {

    private final static String TAG = Conversions.class.getSimpleName();

    private final static int DECIMAL_POINTS = 1;

    public static String degreeToDirection (double degree ) {
        Log.d(TAG, "Degree is: " + degree);

        if (degree >= 348.75 || degree <= 11.25) {
            return "N";
        } else if (degree > 11.25 && degree <= 33.75) {
            return "NNE";
        } else if (degree > 33.75 && degree <= 56.25) {
            return "NE";
        } else if (degree > 56.25 && degree <= 78.75) {
            return "ENE";
        } else if (degree > 78.75 && degree <= 101.25) {
            return "E";
        } else if (degree > 101.25 && degree <= 123.75) {
            return "ESE";
        } else if (degree > 123.75 && degree <= 146.25) {
            return "SE";
        } else if (degree > 146.25 && degree <= 168.75) {
            return "SSE";
        } else if (degree > 168.75 && degree <= 191.25) {
            return "S";
        } else if (degree > 191.25 && degree <= 213.75) {
            return "SSW";
        } else if (degree > 213.75 && degree <= 236.25) {
            return "SW";
        } else if (degree > 236.25 && degree <= 258.75) {
            return "WSW";
        } else if (degree > 258.75 && degree <= 281.25) {
            return "W";
        } else if (degree > 281.25 && degree <= 303.75) {
            return "WNW";
        } else if (degree > 303.75 && degree <= 326.25) {
            return "NW";
        } else {
            return "NNW";
        }

    }//degreeToDirection

    public static String meterToKmh (double meter) {
        Log.d(TAG, "m/s is: " + meter);
        double kmh = meter * 3.6;
        //TODO: use locale
        String output = String.format("%.1f", kmh);
        return output;
    }//meterToKmh

    public static String farToCel (double far) {
        double cel = (far - 32) * 5 / 9;
        String output = String.format("%.0f", cel);
        return output;
    }//farToCel

    public static String decimalToPercentage(double dec) {
        double perc = dec * 100;
        String output = String.format("%.0f", perc);
        return output;
    }//decimalToPercentage

    public static String capitalizeFirst (String s) {
        //check to makes sure it is not empty
        if (s == null || TextUtils.isEmpty(s)) {
            return "";
        }
        String output = s.substring(0,1).toUpperCase() + s.substring(1);
        return output;
    }//capitalizeFirst

    public static String mileToKm (double mile) {
        double km = mile * 1.60934;
        String output = String.format("%.0f", km);
        return output;
    }//mileToKm

    public static String removeDecimal (double d) {
        String output = String.format("%.0f", d);
        return output;
    }//removeDecimal

    //helper method for limiting the decimal points of a string
    private String limitDecimal(String s) {
        if (!(s.contains("."))) {
            return s;
        } else {
            int index = s.indexOf(".");
            return s.substring(0, index + DECIMAL_POINTS + 1);
        }
    }//onDecimal

}