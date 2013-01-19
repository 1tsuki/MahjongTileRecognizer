package com.astrider.mahjongTileRecognizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DMatch;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgproc.Imgproc;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.util.Log;

public class CaptureHelper {
	public static final int ASPECT_RATIO = 4/3;
	public static final int DEFAULT_WIDTH = 30;
	public static final int DEFAULT_HEIGHT = DEFAULT_WIDTH * ASPECT_RATIO;
	public static final int TILE_NUM = 14;
	public static final int TEMPLATE_NUM = 33;
	public static final int THRESHOLD = 45;
	public static final int METHOD_EUCLIDEANDISTANCE = 0;
	public static final int METHOD_ORB = 1;
	public static final int METHOD_ORB_ADVANCED = 2;
	
	Resources res;
	String packageName;
	
	boolean isSourceLoaded = false;
	int methodType = METHOD_ORB_ADVANCED;
	
	HashMap<String, Bitmap> templates = new HashMap<String, Bitmap>();
	FeatureDetector detector = FeatureDetector.create(FeatureDetector.ORB);
	DescriptorExtractor extractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
	DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
	DescriptorMatcher rMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
	DescriptorMatcher gMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
	DescriptorMatcher bMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
	
	Bitmap[] slicedImages = new Bitmap[TILE_NUM];
	int[] detectedTileIds = new int[TILE_NUM];
	String[] detectedTileNames = new String[TILE_NUM];
	float[] similarities = new float[TILE_NUM];
		
	// constructors
	public CaptureHelper(Resources res, String packageName, int methodType) {
		this.isSourceLoaded = false;
		this.res = res;
		this.packageName = packageName;
		this.methodType = methodType;
		loadTemplates();
	}
	
	public CaptureHelper(Bitmap sourceImage, Resources res, String packageName, int methodType) {
		this.isSourceLoaded = false;
		this.res = res;
		this.packageName = packageName;
		this.methodType = methodType;
		sliceImage(sourceImage);
		loadTemplates();
	}
	
	// getters
	public Bitmap[] getSlicedImages() {
		return slicedImages;
	}

	public float[] getSimilarities() {
		return similarities;
	}
	
	public String[] getDetectedTileNames() {
		return detectedTileNames;
	}
	
	public int[] getDetectedTileIds() {
		return detectedTileIds;
	}
	
	// setters
	public void setSourceImage(Bitmap sourceImage) {
		sliceImage(sourceImage);
	}
	
	public void setMethod(int methodType) {
		this.methodType = methodType;
		loadTemplates();
	}
	
	// static methods
	public static Bitmap convertResolution(Bitmap src) {
		// check current resolution
		int srcWidth = src.getWidth();
		int srcHeight = src.getHeight();
		float dstWidth = DEFAULT_WIDTH;
		float dstHeight = DEFAULT_HEIGHT;
		
		// return if convert is not needed
		if(srcWidth == dstWidth && srcHeight == dstHeight) {
			return src;
		}
		
		// convert resolution
		Matrix matrix = new Matrix();
		matrix.postScale(dstWidth / srcWidth, dstHeight / srcHeight);
		Bitmap dst = Bitmap.createBitmap(src, 0, 0, srcWidth, srcHeight, matrix, true);
		
		return dst;
	}

	public static Bitmap binarization( Bitmap bitmap ){
		if( bitmap == null ){
			return bitmap;
		}

		if( bitmap.isMutable( ) != true ){
			bitmap = bitmap.copy( Bitmap.Config.ARGB_8888, true );
		}

		int height   = bitmap.getHeight( );
		int width    = bitmap.getWidth( );
		int[] pixels = new int[( width * height )];
		bitmap.getPixels( pixels, 0, width, 0, 0, width, height );

		for( int YY = 0; YY < width; ++YY ){
			for( int XX = 0; XX < height; ++XX ){
				int bitmapColor = pixels[( YY + XX * width )];
        		int rr = Color.red( bitmapColor );
        		int gg = Color.green( bitmapColor );
        		int bb = Color.blue( bitmapColor );

        		int X, Y;
        		Y = ( rr + gg + bb ) / 3;

        		if( Y < 128 ){
        			X = 0;
        		} else {
        			X = 255;
        		}

        		rr = X;
        		gg = X;
        		bb = X;

        		pixels[( YY + XX * width )] = Color.rgb( rr, gg, bb );
      		}
    	}

    	bitmap.setPixels( pixels, 0, width, 0, 0, width, height );
    	return bitmap;
	}
	
	public static String idToName(int id) {
		if(id == -1) return "”’";
		
		String name = "";
		String[] headers = {"äÝ", "“›", "õ"};
		String[] words = {"“Œ", "“ì", "¼", "–k", "”­", "’†"};
		
		int quotient = id / 9;
		int remainder = id % 9;
		
		if(quotient < 3) {
			name = headers[quotient] + String.valueOf(remainder + 1);
		} else if(quotient == 3) {
			name = words[remainder];
		} else {
			name = "";
		}
		
		return name;
	}
	
	public static float[][] getTileCoords(float width, float height) {
		float[][] tiles = new float[TILE_NUM][4];
		
		float[] unitSize = getUnitSize(width, height);
		float unitWidth = unitSize[0];
		float unitHeight = unitSize[1];
		
//		float left = unitWidth;
		float left = 0;
		float bottom = height / 2;
		
		for (int i = 0; i < tiles.length; i++) {
			tiles[i][0] = left + unitWidth*i;
			tiles[i][1] = bottom - unitHeight;
			tiles[i][2] = left + unitWidth*(i+1);
			tiles[i][3] = bottom;
		}
		
		return tiles;
	}
	
	public static float[] getUnitSize(float width, float height) {
		float[] unitSize = new float[2];
		float unitWidth = width / 14;
		float unitHeight = unitWidth * 4/3;
		
		unitSize[0] = unitWidth;
		unitSize[1] = unitHeight;
		
		return unitSize;
	}

	// public methods
	public String[] identifyTiles() throws Exception {
		if (!isSourceLoaded) {
			throw new Exception("Source Image not loaded");
		}
		
		switch (methodType) {
			case METHOD_EUCLIDEANDISTANCE:
				euclideanDistanceDetection();
				break;
				
			case METHOD_ORB:
				orbDetection();
				break;
				
			case METHOD_ORB_ADVANCED:
				orbAdvancedDetection();
				break;
	
			default:
				orbAdvancedDetection();
				break;
		}
		
		return detectedTileNames;
	}
	
	// private methods
	private void loadTemplates() {
		switch (methodType) {
			case METHOD_EUCLIDEANDISTANCE:
				setupEuclideanDistanceTemplates();
				break;
				
			case METHOD_ORB:
				setupORBMatcher();
				break;
				
			case METHOD_ORB_ADVANCED:
				setupORBAdvancedMatchers();
				break;

			default:
				setupORBAdvancedMatchers();
				break;
		}
	}
	
	private void sliceImage(Bitmap sourceImage) {
		float[][] coords = getTileCoords(sourceImage.getWidth(), sourceImage.getHeight());
		
		for (int i = 0; i < coords.length; i++) {
			int x = (int) coords[i][0];
			int y = (int) coords[i][1];
			int width = (int) coords[i][2] - x;
			int height = (int) coords[i][3] - y;
			
			Bitmap slicedImage = Bitmap.createBitmap(sourceImage, x, y, width, height);
			slicedImages[i] = slicedImage;
		}
	}
	
	private int convertId(int srcId, int mainColor) {
		if(srcId == -1) {
			return srcId;
		}
		
		int id = -1;
		if(mainColor == Color.RED) {
			if(srcId < 9) {
				id = srcId;
			} else if(srcId < 11) {
				id = srcId + 5;
			} else if(srcId < 16){
				id = srcId + 16;
			} else {
				id = srcId + 17;
			}
		}
		
		if(mainColor == Color.GREEN) {
			if(srcId < 9) {
				id = srcId + 9;
			} else {
				id = srcId + 18;
			}
		}
		
		if(mainColor == Color.BLUE) {
			if(srcId < 7) {
				id = srcId + 18;
			} else {
				id = srcId + 20;
			}
		}
		
		return id;
	}
	
	private int[] getRGBAverage() {
		int[] sum = new int[3];
		for (int i = 0; i < sum.length; i++) {
			sum[i] = 0;
		}
		
		for (Bitmap bitmap : slicedImages) {
			int[] bitmapSum = getRGBSum(bitmap);
			for (int i = 0; i < bitmapSum.length; i++) {
				sum[i] = bitmapSum[i];
			}
		}
		
		int[] avg = new int[3];
		for (int i = 0; i < avg.length; i++) {
			avg[i] = sum[i] / slicedImages.length;
		}
		
		return avg;
	}
	
	private int[] getRGBSum(Bitmap bitmap) {
		int height   = bitmap.getHeight( );
		int width    = bitmap.getWidth( );
		int[] pixels = new int[( width * height )];
		bitmap.getPixels( pixels, 0, width, 0, 0, width, height );

		int[] sum = new int[3];
		for( int YY = 0; YY < width; ++YY ){
			for( int XX = 0; XX < height; ++XX ){
				int bitmapColor = pixels[( YY + XX * width )];
        		sum[0] += Color.red( bitmapColor );
        		sum[1] += Color.green( bitmapColor );
        		sum[2] += Color.blue( bitmapColor );
      		}
    	}
		
		return sum;
	}
	
	private int getMainColor(Bitmap bitmap, int[] average) {
		int[] colorSum = getRGBSum(bitmap);
		
		int tmpVal = 0;
		int maxKey = -1;
		for (int i = 0; i < colorSum.length; i++) {
			colorSum[i] -= average[i];
			if (tmpVal > colorSum[i]) {
				tmpVal = colorSum[i];
				maxKey = i;
			}
		}
		
		int[] colorArray = {Color.RED, Color.GREEN, Color.BLUE};
		return colorArray[maxKey];
	}
	
	private double compareImage(Bitmap src, Bitmap target) {
		// check and convert resolution
		src = convertResolution(src);
		target = convertResolution(target);
		
		// get pixels
		int[] srcPixels = new int[DEFAULT_WIDTH * DEFAULT_HEIGHT];
		src.getPixels(srcPixels, 0, DEFAULT_WIDTH, 0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);

		int[] targetPixels = new int[DEFAULT_WIDTH * DEFAULT_HEIGHT];
		target.getPixels(targetPixels, 0, DEFAULT_WIDTH, 0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
		
		// calculate diff
		int srcColor = 0;
		int targetColor = 0;
		int rDiff = 0;
		int gDiff = 0;
		int bDiff = 0;
		int rDiffSum = 0;
		int gDiffSum = 0;
		int bDiffSum = 0;
		
		for (int x = 0; x < DEFAULT_WIDTH; x++) {
			for (int y = 0; y < DEFAULT_HEIGHT; y++) {
				srcColor = srcPixels[x + y*DEFAULT_WIDTH];
				targetColor = targetPixels[x + y*DEFAULT_WIDTH];
				
				rDiff = Color.red(srcColor) - Color.red(targetColor);
				gDiff = Color.green(srcColor) - Color.green(targetColor);
				bDiff = Color.blue(srcColor) - Color.blue(targetColor);
				
				rDiffSum += Math.pow(rDiff, 2);
				rDiffSum += Math.pow(gDiff, 2);
				rDiffSum += Math.pow(bDiff, 2);
			}
		}
		
		double maxDiff = Math.sqrt(DEFAULT_WIDTH * DEFAULT_HEIGHT * Math.pow(255, 2));
		double denominator = 3 * maxDiff;
		double numerator = Math.sqrt(rDiffSum) + Math.sqrt(gDiffSum) + Math.sqrt(bDiffSum);
		
		return numerator / denominator * 100;
	}
	
	private void setupEuclidianDistanceTemplates() {
		
	}
	
	private void setupORBMatcher() {
		int[] ids = {R.drawable.w1, R.drawable.w2, R.drawable.w3, R.drawable.w4, R.drawable.w5, R.drawable.w6, R.drawable.w7, R.drawable.w8, R.drawable.w9, 
					R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4, R.drawable.p5, R.drawable.p6, R.drawable.p7, R.drawable.p8, R.drawable.p9,  
					R.drawable.s1, R.drawable.s2, R.drawable.s3, R.drawable.s4, R.drawable.s5, R.drawable.s6, R.drawable.s7, R.drawable.s8, R.drawable.s9,
					R.drawable.j1, R.drawable.j2, R.drawable.j3, R.drawable.j4, R.drawable.j5, R.drawable.j6};
		
		List<Mat> descriptors = getDescriptors(ids);
		matcher.add(descriptors);
	}
	
	private void setupORBAdvancedMatchers() {
		int[] rIds = {R.drawable.w1, R.drawable.w2, R.drawable.w3, R.drawable.w4, R.drawable.w5, R.drawable.w6, R.drawable.w7, R.drawable.w8, R.drawable.w9, 
					R.drawable.p6, R.drawable.p7, 
					R.drawable.j1, R.drawable.j2, R.drawable.j3, R.drawable.j4, R.drawable.j6};
		
		int[] gIds = {R.drawable.s1, R.drawable.s2, R.drawable.s3, R.drawable.s4, R.drawable.s5, R.drawable.s6, R.drawable.s7, R.drawable.s8, R.drawable.s9,
					R.drawable.j1, R.drawable.j2, R.drawable.j3, R.drawable.j4, R.drawable.j5, };
		
		int[] bIds = {R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4, R.drawable.p5, R.drawable.p8, R.drawable.p9,
					R.drawable.j1, R.drawable.j2, R.drawable.j3, R.drawable.j4, };
		
		// array to contain descriptors
		List<Mat> rDescriptors = getDescriptors(rIds);
		List<Mat> gDescriptors = getDescriptors(gIds);
		List<Mat> bDescriptors = getDescriptors(bIds);
		
		// add descriptors to matcher
		rMatcher.add(rDescriptors);
		gMatcher.add(gDescriptors);
		bMatcher.add(bDescriptors);
	}
	
	private List<Mat> getDescriptors(int[] ids) {
		List<Mat> descriptors = new ArrayList<Mat>();
		for (int i=0; i<ids.length; i++) {
			Bitmap src = BitmapFactory.decodeResource(res, ids[i]);
			
			// convert bitmap to gray mat
			Mat mat = new Mat();
			MatOfKeyPoint keypoint = new MatOfKeyPoint();
			Mat descriptor = new Mat();
			Utils.bitmapToMat(src, mat);
			Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);
			
			// detect, extract and add to descriptors
			detector.detect(mat, keypoint);
			extractor.compute(mat, keypoint, descriptor);
			descriptors.add(descriptor);
			
			// remove src from memory
			src.recycle();
			src = null;
		}
		
		return descriptors;
	}
	
	private void euclideanDistanceDetection() {
		
	}
	
	private void orbDetection() {
		
	}
	
	private void orbAdvancedDetection() {
		for (int i=0; i<slicedImages.length; i++) {
			// load next sliced tile image
			Bitmap target = slicedImages[i];
			int[] averageColor = getRGBAverage();
			int mainColor = getMainColor(target, averageColor);
			
			Log.d("TAG", String.valueOf(mainColor));
			
			DescriptorMatcher matcher = null; 
			switch (mainColor) {
				case Color.RED:
					matcher = rMatcher;
					Log.d("TAG", "red");
					break;
					
				case Color.GREEN:
					matcher = gMatcher;
					Log.d("TAG", "green");
					break;
					
				case Color.BLUE:
					matcher = bMatcher;
					Log.d("TAG", "blue");
					break;
	
				default:
					Log.d("TAG", "fail");
					break;
			}
			
			Mat descriptors = new Mat();
			MatOfKeyPoint keypoint = new MatOfKeyPoint();
			
			// convert bitmap to gray mat
			Mat mat = new Mat();
			Utils.bitmapToMat(target, mat);
			Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);
			
			// detect, extract target image
			detector.detect(mat, keypoint);
			extractor.compute(mat, keypoint, descriptors);
			
			// do matching
			MatOfDMatch matches = new MatOfDMatch();
			matcher.match(descriptors, matches);
			
			// initialize vote box
			int[] votes = new int[TEMPLATE_NUM];
			for (int j = 0; j < TEMPLATE_NUM; j++) {
				votes[j] = 0;
			}
			
			// do vote
			List<DMatch> myList = matches.toList();
			Iterator<DMatch> itr = myList.iterator(); 
			while(itr.hasNext()) 
			{
			      DMatch element = itr.next();
			      if(element.distance < THRESHOLD) {
			    	  votes[element.imgIdx]++;
			      }
			}
			
			int maxImageId = -1;
			int maxVotes = 0;
			for (int j = 0; j < TEMPLATE_NUM; j++) {
				if(votes[j] > maxVotes ) {
					maxImageId = j;
					maxVotes = votes[j];
				}
			}
			
			List<Mat> trainDescs = new ArrayList<Mat>(); 
			trainDescs = matcher.getTrainDescriptors();
			
			// if similarity is under 5%, set as undetected
			float similarity = 0;
			if(maxImageId > 0) {
				similarity = (float)maxVotes/trainDescs.get(maxImageId).rows()*100;
				if(similarity < 5) {
					maxImageId = -1;
				}
			}
			
			detectedTileIds[i] = convertId(maxImageId, mainColor);
			detectedTileNames[i] = idToName(detectedTileIds[i]);
			similarities[i] = similarity;
		}
	}
	
	
	
	private void oldMedthod() {
//		// prepare variables
//		double tmpVal = 0;
//		double minDiff = 100;
//		String tileType = "";
		
		// compare target with each templates
//		for (Entry<String, Bitmap> entry : templates.entrySet()) {
//			tmpVal = compareImage(entry.getValue(), target);
//			
//			if(tmpVal < minDiff) {
//				minDiff = tmpVal;
//				tileType = entry.getKey();
//			}
//		}
//		
//		detectedTiles[i] = tileType;
//		difference[i] = minDiff;
//	}
	
//	return detectedTiles;
	}
}
