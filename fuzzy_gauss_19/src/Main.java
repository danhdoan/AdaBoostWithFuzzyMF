
/**
 * File:        Main.java
 * project:     Fuzzy Membership
 * Author:      Danh Doan
 *
 * Description:
 *      Develop Face Detection system using AdaBoost
 *      with Haar-like as features
 *      also uses Fuzzy Gaussian MF
 *      Train and test by MIT cbcl dataset
 *
 *
 * Revision:
 *      2018-01-25      :
 *          - File created
 *          -.
 *
 */
import java.io.*;
import java.util.*;

public class Main {

    public final static String pathProject = ".";
    public final static String pathInput = pathProject + File.separator + "inputs";
    public final static String pathOutput = pathProject + File.separator + "outputs";

    public final static String pathTypePattern = pathProject + File.separator + "typePattern";

    public final static String pathTrain = pathInput + File.separator + "train";
    public final static String pathTrainInfo = pathTrain + File.separator + "info.txt";

    public final static String pathTest = pathInput + File.separator + "test";
    public final static String pathTestInfo = pathTest + File.separator + "info.txt";

    public final static String pathWeakClass = pathOutput + File.separator + "weakclass"
            + File.separator + "20180125_fuzzy_gauss2_19.wk";

    public static String face = File.separator + "face";
    public static String nonFace = File.separator + "non-face";

    public static Random r = new Random();

    public final static int numWeakClass = 200;

    public static int numFeature = 134541;
    public final static int numType = 5;

    public static int numFeatureSel = 5000;

    public static ArrayList<int[][]> lstImage = null;
    public static ArrayList<int[][]> lstIntegral = null;
    public static ArrayList<int[][]> lstImageTest = null;
    public static ArrayList<int[][]> lstIntegralTest = null;
    public static ArrayList<int[][]> lstType = null;
    public static IndexObj[] lstIndexObj = null;

    public static void main(String[] args) {
        trainingProcess();
        testingProcess(pathTrain, pathTrainInfo, 0.5, 0.5);
        testingProcess(pathTest, pathTestInfo, 0.2, 0.8);
    }

    public static void testingProcess(String pathImg, String pathInfo, double low, double high) {
        lstImageTest = readInputImage(pathImg);
        lstIntegralTest = calculateIntegralImage(lstImageTest);

        lstType = readTypePattern(pathTypePattern);
        lstIndexObj = readIndex(pathTypePattern);

        double rate = low;
        while (rate <= high) {
            testData(pathInfo, rate);
            rate += 0.01;
        }
    }

    public static void trainingProcess() {
        lstImage = readInputImage(pathTrain);
        System.out.println(lstImage.size());

        lstIntegral = calculateIntegralImage(lstImage);
        System.out.println(lstIntegral.size());

        lstType = readTypePattern(pathTypePattern);
        System.out.println(lstType.size());

        lstIndexObj = readIndex(pathTypePattern);
        System.out.println(lstIndexObj.length);

        WeakClass[] wk = algorithmAdaboost();
        saveArrWeakClass(wk, pathWeakClass);
    }

    public static void testData(String fileTestInfo, double rate) {
        int numPosTest = 0, numNegTest = 0;

        try {
            BufferedReader br = new BufferedReader(new FileReader(fileTestInfo));
            numPosTest = Integer.parseInt(br.readLine());
            numNegTest = Integer.parseInt(br.readLine());
            br.close();
        } catch (IOException | NumberFormatException e) {
        }

        WeakClass[] wk = loadWeakClass(pathWeakClass);
        try {
            int cntPos = 0, cntNeg = 0;
            for (int i = 0; i < numPosTest + numNegTest; i++) {
                double sumAlpha = 0, sum = 0;

                for (int j = 0; j < numWeakClass; j++) {
                    WeakClass wk1 = wk[j];
                    sumAlpha += wk1.alpha;
                    int idx = wk1.fi.getIndex();
                    int type = lstIndexObj[idx].getType();
                    int x = lstIndexObj[idx].getX();
                    int y = lstIndexObj[idx].getY();
                    int h = lstIndexObj[idx].getH();
                    int w = lstIndexObj[idx].getW();
                    FeatureTemplate ftemp = new FeatureTemplate(lstType.get(type - 1));
                    double val = ftemp.getFeatureValue(
                            lstIntegralTest.get(i), x, y, x + h - 1, y + w - 1);

                    FeatureIndex fwk = wk1.fi;
                    if (isPositiveDist(fwk.getMeanPos(), fwk.getVarPos(),
                            fwk.getMeanNeg(), fwk.getVarNeg(), val, fwk.getGamma())) {
                        sum += wk1.alpha;

                    }
                }

                sumAlpha *= rate;
                if (sum >= sumAlpha) {
                    if (i < numPosTest) {
                        cntPos++;
                    }
                } else {
                    if (i >= numPosTest) {
                        cntNeg++;
                    }
                }
            }

            //double err = 1 - 1. * (cntPos + cntNeg) / (numPosTest + numNegTest);
            //System.out.printf("%d %f\n", len, err);
            System.out.printf("%.2f %.3f %.3f\n", rate,
                    100. * cntPos / numPosTest,
                    100. * (1 - cntNeg * 1. / numNegTest)
            );

        } catch (Exception e) {
            System.out.println("err:" + e.getMessage());
        }
    }

    public static WeakClass[] algorithmAdaboost() {
        System.out.println("algorithmAdaboost");
        int numPosTrain = 0, numNegTrain = 0;

        try {
            BufferedReader br = new BufferedReader(new FileReader(pathTrainInfo));
            numPosTrain = Integer.parseInt(br.readLine());
            numNegTrain = Integer.parseInt(br.readLine());
            br.close();
        } catch (IOException | NumberFormatException e) {
            System.out.println(e.getMessage());
        }

        double[] weights = new double[numPosTrain + numNegTrain];

        for (int i = 0; i < numPosTrain; i++) {
            weights[i] = 1.0 / (2 * numPosTrain);
        }
        for (int i = numPosTrain; i < numPosTrain + numNegTrain; i++) {
            weights[i] = 1.0 / (2 * numNegTrain);
        }

        WeakClass[] arrWeakClass = new WeakClass[numWeakClass];

        for (int t = 0; t < numWeakClass; t++) {
            long tStart = new Date().getTime();

            System.out.println("Weak Class: " + t);
            double res = 0.;
            for (int i = 0; i < numPosTrain + numNegTrain; i++) {
                res += weights[i];
            }
            for (int i = 0; i < numPosTrain + numNegTrain; i++) {
                weights[i] /= res;
            }

            FeatureIndex bestFeature = getFeatureIndex_weights(weights,
                    numPosTrain, numNegTrain);

            double err = bestFeature.getError();
            /*
            if (err >= 0.5 && Math.abs(err - 0.5) > 1e-9 ||
                    Math.abs(err - 0.5) < 1e-9) {
                if (t > 0) {
                    arrWeakClass[t] = arrWeakClass[t-1];
                    continue;
                }
                else 
                    return null;
            }
*/
            double beta = err / (1 - err);
            double alpha = Math.log(1 / beta);

            System.out.println("Index: " + bestFeature.getIndex());
            System.out.println("Error: " + bestFeature.getError());
            System.out.println("Beta: " + beta);

            arrWeakClass[t] = new WeakClass(bestFeature, alpha);

            for (int idxImg = 0; idxImg < numPosTrain + numNegTrain; idxImg++) {
                int label = idxImg < numPosTrain ? 1 : 0;
                int ei = label == bestFeature.getHt()[idxImg] ? 0 : 1;
                weights[idxImg] *= Math.pow(beta, 1 - ei);
            }

            bestFeature.setHt(null);
            long tEnd = new Date().getTime();
            System.out.println("Time: " + (tEnd - tStart) / 1000. + "\n");
        }
        return arrWeakClass;
    }

    private static FeatureIndex getFeatureIndex_weights(double[] weights,
            int numPosTrain, int numNegTrain) {
        int[] indexRandom = genIndex(numFeature, numFeatureSel);

        FeatureIndex bestFeature = null;
        double minErr = Double.MAX_VALUE;
        //for (int idx = 0; idx < lstIndexObj.length; idx++) {
        for (int k = 0; k < indexRandom.length; k++) {
            int idx = indexRandom[k];
            FeatureIndex obj = new FeatureIndex(idx);

            int type = lstIndexObj[idx].getType();
            int x = lstIndexObj[idx].getX();
            int y = lstIndexObj[idx].getY();
            int h = lstIndexObj[idx].getH();
            int w = lstIndexObj[idx].getW();

            double[] ftValue = new double[numPosTrain + numNegTrain];
            FeatureTemplate ftemp = new FeatureTemplate(lstType.get(type - 1));
            for (int i = 0; i < ftValue.length; i++) {
                ftValue[i] = ftemp.getFeatureValue(
                        lstIntegral.get(i), x, y, x + h - 1, y + w - 1);
            }

            double meanPos = getMean(ftValue, weights, 0, numPosTrain - 1);
            double varPos = getVariance(ftValue, weights, 0, numPosTrain - 1, meanPos);

            double meanNeg = getMean(ftValue, weights, numPosTrain, numPosTrain + numNegTrain - 1);
            double varNeg = getVariance(ftValue, weights, numPosTrain, numPosTrain + numNegTrain - 1, meanNeg);

            int[] ht = new int[numPosTrain + numNegTrain];

            double gamma = 1;

            double err = 0.;
            for (int i = 0; i < numPosTrain + numNegTrain; i++) {
                int correctLabel = i < numPosTrain ? 1 : 0;
                int label = isPositiveDist(meanPos, varPos, meanNeg, varNeg, ftValue[i], gamma)
                        ? 1 : 0;

                ht[i] = label;
                if (label != correctLabel) {
                    err += weights[i];
                }
            }

            if (minErr > err) {
                minErr = err;
                obj.setGamma(gamma);
                obj.setError(err);
                obj.setHt(ht);
                obj.setMeanPos(meanPos);
                obj.setVarPos(varPos);
                obj.setMeanNeg(meanNeg);
                obj.setVarNeg(varNeg);
                bestFeature = obj;
            }
        }

        return bestFeature;
    }

    public static Boolean isPositiveDist(double meanPos, double varPos, double meanNeg, double varNeg,
            double val, double gamma) {
        double temp1 = (val - meanPos) * (val - meanPos) / (gamma * varPos);
        double temp2 = (val - meanNeg) * (val - meanNeg) / (gamma * varNeg);

        double left = Math.log(varNeg / varPos);
        double right = 2*(temp1 - temp2);

        return left > right;// && Math.abs(left - right) > 1e-9
                //|| Math.abs(left - right) < 1e-9;
    }

    private static int[] genIndex(int n, int sl) {
        ArrayList<Integer> arrList = new ArrayList<>();
        do {
            int temp = r.nextInt(n);
            if (!arrList.contains(temp)) {
                arrList.add(temp);
            }
        } while (arrList.size() < sl);

        int[] arr = new int[sl];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = arrList.get(i);
        }

        return arr;
    }

    public static ArrayList<int[][]> calculateIntegralImage(ArrayList<int[][]> lstImg) {
        ArrayList<int[][]> lst = new ArrayList<>();
        for (int idx = 0; idx < lstImg.size(); idx++) {
            int[][] img = lstImg.get(idx);
            int[][] dp = new int[img.length + 1][img[0].length + 1];

            for (int i = 1; i < img.length; i++) {
                for (int j = 1; j < img[0].length; j++) {
                    dp[i][j] = img[i][j] + dp[i - 1][j]
                            + dp[i][j - 1] - dp[i - 1][j - 1];
                }
            }
            lst.add(dp);
        }
        return lst;
    }

    public static IndexObj[] readIndex(String path) {
        String pathLookUpTable = path + File.separator + "lookUpTable.txt";
        File file = new File(pathLookUpTable);

        IndexObj[] arrIdxObj = new IndexObj[numFeature];
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            for (int i = 0; i < numFeature; i++) {
                String line = br.readLine();
                Scanner sc = new Scanner(line);
                byte type = sc.nextByte();
                byte x = sc.nextByte();
                byte y = sc.nextByte();
                byte h = sc.nextByte();
                byte w = sc.nextByte();

                IndexObj idxObj = new IndexObj(type, x, y, h, w);
                arrIdxObj[i] = idxObj;
            }
            br.close();
        } catch (IOException e) {

        }

        return arrIdxObj;
    }

    public static ArrayList<int[][]> readTypePattern(String path) {
        ArrayList<int[][]> lst = new ArrayList<>();

        for (int type = 1; type <= numType; type++) {
            String pathType = path + File.separator + "type" + type + ".txt";
            File file = new File(pathType);
            try {
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line = br.readLine();

                Scanner sc = new Scanner(line);
                int H = sc.nextInt();
                int W = sc.nextInt();

                int[][] pattern = new int[H][W];
                for (int i = 0; i < H; i++) {
                    line = br.readLine();
                    sc = new Scanner(line);
                    for (int j = 0; j < W; j++) {
                        pattern[i][j] = sc.nextInt();
                    }
                }
                lst.add(pattern);
                br.close();
            } catch (IOException e) {
            }
        }

        return lst;
    }

    public static ArrayList<int[][]> readInputImage(String path) {
        ArrayList<int[][]> lst = new ArrayList<>();

        String pathTrainFace = path + face;
        File fd_face = new File(pathTrainFace);
        File[] files_face = fd_face.listFiles();
        for (File f : files_face) {
            if (f.isFile()) {
                String fileName = f.getName();
                if (fileName.endsWith(".pgm")) {
                    lst.add(ImageProcessing.readPGMImage(pathTrainFace
                            + File.separator + fileName));
                } else if (fileName.endsWith(".png")) {
                    int[][] img = ImageProcessing.readPNGImage(pathTrainFace
                            + File.separator + fileName);
                    lst.add(ImageProcessing.normalizeImage(img));
                }
            }
        }

        String pathTrainNonFace = path + nonFace;
        File fd_nonFace = new File(pathTrainNonFace);
        File[] files_nonFace = fd_nonFace.listFiles();
        for (File f : files_nonFace) {
            if (f.isFile()) {
                String fileName = f.getName();
                if (fileName.endsWith(".pgm")) {
                    lst.add(ImageProcessing.readPGMImage(pathTrainNonFace
                            + File.separator + fileName));
                } else if (fileName.endsWith(".png")) {
                    int[][] img = ImageProcessing.readPNGImage(pathTrainNonFace
                            + File.separator + fileName);
                    lst.add(ImageProcessing.normalizeImage(img));
                }
            }
        }

        return lst;

    }

    static class Obj implements Comparable<Obj> {

        int index;
        double value;
        double weight;

        public Obj(int i, double v, double w) {
            this.index = i;
            this.value = v;
            this.weight = w;
        }

        @Override
        public int compareTo(Obj arg0) {
            return this.value == arg0.value ? 0 : (this.value < arg0.value ? -1 : 1);
        }
    }

    public static double getMean(double[] ftValue, double[] weight, int idx1, int idx2) {
        double sum = 0;
        double res = 0;
        for (int i = idx1; i <= idx2; i++) {
            res += ftValue[i] * weight[i];
            sum += weight[i];
        }

        return res / sum;
    }

    public static double getVariance(double[] ftValue, double[] weight, int idx1, int idx2, double mean) {
        double sum = 0;
        double res = 0;
        for (int i = idx1; i <= idx2; i++) {
            res += Math.pow((ftValue[i] - mean), 2) * weight[i];
            sum += weight[i];
        }

        return res / sum;
    }

    public static void saveArrWeakClass(WeakClass[] arr, String path) {
        try {
            FileOutputStream fos = new FileOutputStream(new File(path));
            try (ObjectOutputStream out = new ObjectOutputStream(fos)) {
                out.writeObject(arr);

                out.flush();
            }
        } catch (IOException e) {
        }
    }

    public static WeakClass[] loadWeakClass(String path) {
        WeakClass[] arr = null;

        try {
            FileInputStream fis = new FileInputStream(new File(path));
            ObjectInputStream in = new ObjectInputStream(fis);
            arr = (WeakClass[]) in.readObject();

            in.close();
        } catch (Exception e) {
            System.out.println(e);
        }
        return arr;
    }
}
