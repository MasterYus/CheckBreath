package ru.myus.checkbreath.util;

/*
 Please feel free to use/modify this class.
 If you give me credit by keeping this information or
 by sending me an email before using it or by reporting bugs , i will be happy.
 Email : gtiwari333@gmail.com,
 Blog : http://ganeshtiwaridotcomdotnp.blogspot.com/
*/

/**
 * calculates energy from given PCM of a frame
 *
 * @author Madhav Pandey
 * @reference Spectral Features for Automatic Text-Independent Speaker
 *            Recognition @author Tomi Kinnunen, @fromPage ##
 */
public class Energy {

    /**
     *
     * @param framedSignal
     * @return energy of given PCM frame
     */
    public double[] calcEnergy(float[][] framedSignal) {
        double[] energyValue = new double[framedSignal.length];
        for (int i = 0; i < framedSignal.length; i++) {
            float sum = 0;
            for (int j = 0; j < framedSignal[i].length; j++) {
                // sum the square
                sum += Math.pow(framedSignal[i][j], 2);
            }
            // find log
            energyValue[i] = Math.log(sum);
        }
        return energyValue;
    }

    public double calcEnergy(float[] frame) {
        float sum = 0;
        for (int i = 0; i < frame.length; i++) {
            // sum the square
                sum += Math.pow(frame[i], 2);
        }
        return Math.log(sum);
    }
}