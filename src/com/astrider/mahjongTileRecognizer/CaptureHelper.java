package com.astrider.mahjongTileRecognizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.DMatch;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.KeyPoint;
import org.opencv.imgproc.Imgproc;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;

public class CaptureHelper {
	public static final int DEFAULT_WIDTH = 63;
	public static final int DEFAULT_HEIGHT = 84;
	public static final int TILE_NUM = 9;
	public static final int TEMPLATE_NUM = 33;
	public static final int THRESHOLD = 45;
	public static final int METHOD_EUCLIDEANDISTANCE = 0;
	public static final int METHOD_ORB = 1;
	public static final int METHOD_ORB_ADVANCED = 2;
	public static final int CONTRAST_LEVEL = 80;
	
	Resources res;
	String packageName;
	
	boolean isSourceLoaded = false;
	int methodType = METHOD_ORB;
	
	int[] templateImageIds = 
		{R.drawable.w1, R.drawable.w2, R.drawable.w3, R.drawable.w4, R.drawable.w5, R.drawable.w6, R.drawable.w7, R.drawable.w8, R.drawable.w9,
		 R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4, R.drawable.p5, R.drawable.p6, R.drawable.p7, R.drawable.p8, R.drawable.p9,
		 R.drawable.s1, R.drawable.s2, R.drawable.s3, R.drawable.s4, R.drawable.s5, R.drawable.s6, R.drawable.s7, R.drawable.s8, R.drawable.s9,
		 R.drawable.j1, R.drawable.j2, R.drawable.j3, R.drawable.j4, R.drawable.j5, R.drawable.j6};
	DescriptorMatcher matcher = null;
	DescriptorMatcher rMatcher = null;
	DescriptorMatcher otherMatcher = null;
	FeatureDetector detector = FeatureDetector.create(FeatureDetector.ORB);
	DescriptorExtractor extractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
	
	Bitmap[] slicedImages = null;
	String[] mainColors = null;
	int[] detectedTileIds = new int[TILE_NUM];
	String[] detectedTileNames = new String[TILE_NUM];
	String[] predetectionResult = new String[TILE_NUM];
	float[] similarities = new float[TILE_NUM];
		
	// constructors
	public CaptureHelper(Resources res, String packageName, int methodType) {
		this.res = res;
		this.packageName = packageName;
		this.methodType = methodType;
		loadTemplates();
	}
	
	public CaptureHelper(Bitmap sourceImage, Resources res, String packageName, int methodType) {
		this.res = res;
		this.packageName = packageName;
		this.methodType = methodType;
		sliceImage(sourceImage);
		loadTemplates();
		
		this.isSourceLoaded = true;
	}
	
	// static methods (tools)
	public static List<MatOfPoint> getContours(Bitmap bitmap) {
		Mat src = new Mat();
		Mat gray = new Mat();
		Mat dst = new Mat();
		Utils.bitmapToMat(bitmap, src);
		
		Imgproc.cvtColor(src , gray, Imgproc.COLOR_RGB2GRAY); 
		Imgproc.threshold(gray, dst, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
		Mat hierarchy = new Mat();
		List<MatOfPoint> contours = new ArrayList<MatOfPoint>(100);
		Imgproc.findContours(dst, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE);
		
		return contours;
	}
	
	public static Bitmap drawContours(Bitmap bitmap, List<MatOfPoint> contours) {
		Mat src = new Mat();
		Utils.bitmapToMat(bitmap, src);
		Imgproc.drawContours(src, contours, -1, new Scalar(255, 0, 0), 1);
		
		Bitmap dst = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(src, dst);
		return dst;
	}
	
	public static Bitmap chopWithContours(Bitmap bitmap, List<MatOfPoint> contours) {
		if(contours.size() < 1) {
			return bitmap;
		}
		
		Mat src = new Mat();
		Utils.bitmapToMat(bitmap, src);
		// ‰‚©‚ç5“‚Ì”ÍˆÍ“à‚Édot‚ðŽ‚Â—ÖŠsü‚Íœ‹Ž
		int padding = (int) (src.width() * 0.05);
		int min_x_thresh = padding;
		int max_x_thresh = src.width() - padding;
		int min_y_thresh = padding;
		int max_y_thresh = src.height() - padding;
		
		Point min = new Point(src.width() / 2, src.height() / 2);
		Point tmpMin = new Point(src.width() / 2, src.height() / 2);
		Point max = new Point(src.width() / 2, src.height() / 2);
		Point tmpMax = new Point(src.width() / 2, src.height() / 2);
		double x, y, tmp[];
		boolean ignoreFlag = false;
		
		// check every contours
		for (int i = 0; i < contours.size(); i++) {
			Mat m = contours.get(i);
			
			// reset values
			ignoreFlag = false;
			tmpMin.x = tmpMax.x = src.width() / 2;
			tmpMin.y = tmpMax.y = src.height() / 2;
			
			// check all dots in contour
			for (int j = 0; j < m.rows(); j++) {
				tmp = m.get(j, 0);
				x = tmp[0]; y = tmp[1];
				
				// if dot is within threshold, break and skip this contour
				if(x < min_x_thresh || max_x_thresh < x || y < min_y_thresh || max_y_thresh < y) {
					ignoreFlag = true;
					break;
				}
				
				// check each dot is min / max
				if(x < tmpMin.x) {
					tmpMin.x = x;
				}
				if(y < tmpMin.y) {
					tmpMin.y = y;
				}
				if(tmpMax.x < x) {
					tmpMax.x = x;
				}
				if(tmpMax.y < y) {
					tmpMax.y = y;
				}
			}
			
			if(!ignoreFlag) {
				if(tmpMin.x < min.x) {
					min.x = tmpMin.x;
				}
				if(tmpMin.y < min.y) {
					min.y = tmpMin.y;
				}
				if(max.x < tmpMax.x) {
					max.x = tmpMax.x;
				}
				if(max.y < tmpMax.y) {
					max.y = tmpMax.y;
				}
			}
		}
			
		Mat sub = src.submat(new Rect(min, max));
		if(sub.width() == 0 || sub.height() == 0) {			
			return bitmap;
		}
		
		Log.d("TAG", "Contour x:" + String.valueOf(min.x) + " to " + String.valueOf(max.x) + " y:" + String.valueOf(min.y) + " to " + String.valueOf(max.y));
		Bitmap dst = Bitmap.createBitmap(sub.width(), sub.height(), Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(sub, dst);
		return dst;
	}
	
	public static Bitmap chopWithBoundingRect(Bitmap bitmap) {
		Mat src = new Mat();
		MatOfKeyPoint keyPoints = new MatOfKeyPoint();
		
		Utils.bitmapToMat(bitmap, src);
		FeatureDetector detector = FeatureDetector.create(FeatureDetector.ORB);
		detector.detect(src, keyPoints);
		
		KeyPoint[] keyPointsArray = keyPoints.toArray();
		if(keyPointsArray.length > 0) {
			List<Point> pointList = new ArrayList<Point>();
			MatOfPoint2f points = new MatOfPoint2f();
			for (int i = 0; i < keyPointsArray.length; i++) {
				pointList.add(keyPointsArray[i].pt);
			}
			points.fromList(pointList);
			 
			// detect bounding rect
			RotatedRect rect = Imgproc.minAreaRect(points);
			
			// extract rectangle
			Mat M = new Mat();
			Mat rotated = new Mat();
			Mat cropped = new Mat();
			double angle = rect.angle;
			Size rect_size = rect.size;
			
			if( rect.angle < -45.) {
				angle += 90.0;
				double tmp = rect_size.width;
				rect_size.width = rect_size.height;
				rect_size.height = tmp;
			}
			M = Imgproc.getRotationMatrix2D(rect.center, angle, 1.0);
			Imgproc.warpAffine(src, rotated, M, src.size(), Imgproc.INTER_CUBIC);
			Imgproc.getRectSubPix(rotated, rect_size, rect.center, cropped);
			
			Utils.matToBitmap(cropped, bitmap);
		}
		
		return bitmap;
	}
	
	public static Bitmap effectChangeResolution(Bitmap src, int dstWidth, int dstHeight) {
		return Bitmap.createScaledBitmap(src, dstWidth, dstHeight, false);
	}
	
	public static Bitmap effectWhiten(Bitmap bitmap, int threshold) {
		if( bitmap == null ){
			return bitmap;
		}
		
		Bitmap dst = bitmap.copy( Bitmap.Config.ARGB_8888, true );

		int height   = dst.getHeight( );
		int width    = dst.getWidth( );
		int[] pixels = new int[( width * height )];
		dst.getPixels( pixels, 0, width, 0, 0, width, height );

		for( int YY = 0; YY < width; ++YY ){
			for( int XX = 0; XX < height; ++XX ){
				int bitmapColor = pixels[( YY + XX * width )];
				int[] color = {Color.red(bitmapColor), Color.green(bitmapColor), Color.blue(bitmapColor)};
				
				if (color[0] > threshold && color[1] > threshold && color[2] > threshold) {
					color[0] = 255;
					color[1] = 255;
					color[2] = 255;
				}
        		pixels[( YY + XX * width )] = Color.rgb( color[0], color[1], color[2] );
      		}
    	}
		
    	dst.setPixels( pixels, 0, width, 0, 0, width, height );
    	return dst;
	}
	
	public static Bitmap effectChangeContrast(Bitmap bitmap, double effectLevel) {
		if( bitmap == null ){
			return bitmap;
		}
		
		Bitmap dst = bitmap.copy( Bitmap.Config.ARGB_8888, true );

		int height   = dst.getHeight( );
		int width    = dst.getWidth( );
		int[] pixels = new int[( width * height )];
		dst.getPixels( pixels, 0, width, 0, 0, width, height );

		for( int YY = 0; YY < width; ++YY ){
			for( int XX = 0; XX < height; ++XX ){
				int bitmapColor = pixels[( YY + XX * width )];
				int[] color = {Color.red(bitmapColor), Color.green(bitmapColor), Color.blue(bitmapColor)};
				
				if (color[0] > 200 && color[1] > 200 && color[3] > 200) {
					color[0] = 255;
					color[1] = 255;
					color[2] = 255;
				}
				
				for (int i = 0; i < color.length; i++) {
					int tmpColor = color[i];
					tmpColor = (int) ((tmpColor - 128) * effectLevel + 128);
					if(tmpColor < 0) {
						tmpColor = 0;
					} else if (tmpColor > 255) {
						tmpColor = 255;
					}
					color[i] = tmpColor;
				}

        		pixels[( YY + XX * width )] = Color.rgb( color[0], color[1], color[2] );
      		}
    	}
		
		
    	dst.setPixels( pixels, 0, width, 0, 0, width, height );
    	return dst;
	}

	public static int[] getRGBSum(Bitmap bitmap) {
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
	
	public static String getMainColor(Bitmap bitmap) {
		int[] colorSum = getRGBSum(bitmap);
		
		int tmpVal = 0;
		int maxKey = 0;
		for (int i = 0; i < colorSum.length; i++) {
			if (tmpVal < colorSum[i]) {
				tmpVal = colorSum[i];
				maxKey = i;
			}
		}
		
		String[] colorArray = {"RED", "GREEN", "BLUE"};
		return colorArray[maxKey];
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
		
		float left = unitWidth;
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
		float unitWidth = width / (TILE_NUM + 2);
		float unitHeight = unitWidth * 4/3;
		
		unitSize[0] = unitWidth;
		unitSize[1] = unitHeight;
		
		return unitSize;
	}

	// public methods
	public long identifyTiles() throws Exception {
		if (!isSourceLoaded) {
			throw new Exception("Source Image not loaded");
		}
		
		long time;
		switch (methodType) {
			case METHOD_EUCLIDEANDISTANCE:
				Log.d("TAG", "method:" + "euc");
				time = euclideanDistanceDetection();
				break;
				
			case METHOD_ORB:
				Log.d("TAG", "method:" + "orb");
				time = orbDetection();
				break;
				
			case METHOD_ORB_ADVANCED:
				Log.d("TAG", "method:" + "aorb");
				time = orbAdvancedDetection();
				break;
	
			default:
				Log.d("TAG", "method:" + "err");
				time = orbAdvancedDetection();
				break;
		}
		
		return time;
	}
	
	public String[] getMainColors() {
		mainColors = new String[TILE_NUM];
		for (int i = 0; i < slicedImages.length; i++) {
			mainColors[i] = getMainColor(slicedImages[i]);
		}
		
		return mainColors;
	}
	
	public void recycleSlicedImages() {
		Log.d("TAG", "Recycled sliced images");
		if(slicedImages != null) {
			for (int i = 0; i < slicedImages.length; i++) {
				if(slicedImages[i] != null) {
					slicedImages[i].recycle();
					slicedImages[i] = null;
				}
			}
		}
	}
	
	// private methods
	private void loadTemplates() {
		switch (methodType) {
			case METHOD_EUCLIDEANDISTANCE:
				setupEuclidianDistanceTemplates();
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
		Log.d("TAG", "Slice Image");
		slicedImages = new Bitmap[TILE_NUM];
		
		float[][] coords = getTileCoords(sourceImage.getWidth(), sourceImage.getHeight());
		
		for (int i = 0; i < coords.length; i++) {
			int x = (int) coords[i][0];
			int y = (int) coords[i][1];
			int width = (int) coords[i][2] - x;
			int height = (int) coords[i][3] - y;
			
			Bitmap slicedImage = Bitmap.createBitmap(sourceImage, x, y, width, height);
			slicedImage = effectWhiten(slicedImage, CONTRAST_LEVEL);
			slicedImages[i] = slicedImage;
		}
	}
	
	private int advancedId2NormalId(int srcId, String mainColor) {
		if(srcId == -1) {
			return srcId;
		}
		
		int id = -1;
		if(mainColor == "RED") {
			if(srcId < 9) {
				id = srcId;
			} else if(srcId < 11) {
				id = srcId + 5;
			} else if(srcId < 15){
				id = srcId + 16;
			} else {
				id = srcId + 17;
			}
		}
		
		if(mainColor == "GREEN" || mainColor == "BLUE") {
			id = srcId + 9;
		}
		
		return id;
	}
	
	// helper methods for loading resource
	private void setupEuclidianDistanceTemplates() {
		Log.d("TAG", "setupEuclidianDistanceTemplates");
	}
	
	private void setupORBMatcher() {
		Log.d("TAG", "setupORBMatcher");
		matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
		List<Mat> descriptors = getDescriptors(templateImageIds);
		matcher.add(descriptors);
	}
	
	private void setupORBAdvancedMatchers() {
		Log.d("TAG", "setupORBAdvancedMatcher");
		rMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
		otherMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
		int[] rIds = {R.drawable.w1, R.drawable.w2, R.drawable.w3, R.drawable.w4, R.drawable.w5, R.drawable.w6, R.drawable.w7, R.drawable.w8, R.drawable.w9, 
					  R.drawable.p6, R.drawable.p7, R.drawable.j1, R.drawable.j2, R.drawable.j3, R.drawable.j4, R.drawable.j6};
		
		int[] otherIds = {R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4, R.drawable.p5, R.drawable.p6, R.drawable.p7, R.drawable.p8, R.drawable.p9,
						  R.drawable.s1, R.drawable.s2, R.drawable.s3, R.drawable.s4, R.drawable.s5, R.drawable.s6, R.drawable.s7, R.drawable.s8, R.drawable.s9,
						  R.drawable.j1, R.drawable.j2, R.drawable.j3, R.drawable.j4, R.drawable.j5, R.drawable.j6};
		
		// array to contain descriptors
		List<Mat> rDescriptors = getDescriptors(rIds);
		List<Mat> otherDescriptors = getDescriptors(otherIds);
		
		// add descriptors to matcher
		rMatcher.add(rDescriptors);
		otherMatcher.add(otherDescriptors);
	}
	
	// detectors
	private long euclideanDistanceDetection() {
		Log.d("TAG", "Runninng euclidean Distance Detection");
		long start = System.currentTimeMillis();
		
		// prepare variables
		Bitmap target = null;
		Bitmap template = null;
		double tmpDiff = 0;
		double minDiff = 100;
		int detected = 0;
		
		// compare target with each templates
		for (int i = 0; i < slicedImages.length; i++) {
			target = chopWithContours(slicedImages[i], getContours(slicedImages[i]));
			
			// compare with templates
			for (int j = 0; j < templateImageIds.length; j++) {
				template = BitmapFactory.decodeResource(res, templateImageIds[j]);
				tmpDiff = getEuclideanDistance(target, template);
				if(tmpDiff < minDiff) {
					minDiff = tmpDiff;
					detected = j;
				}
				
				template.recycle();
			}
			detectedTileIds[i] = detected;
			detectedTileNames[i] = idToName(detectedTileIds[i]);
			similarities[i] = (float) (100 - minDiff);
			
			// reset values
			minDiff = 100;
			target.recycle();
		}
		
		long end = System.currentTimeMillis();
		return end - start;
	}
	
	private long orbDetection() {
		Log.d("TAG", "Runninng orb Detection");
		long start = System.currentTimeMillis();
		for (int i=0; i<slicedImages.length; i++) {
			// load next sliced tile image
			Bitmap target = slicedImages[i];
			HashMap<String, Number> result = matchImage(matcher, target);
			
			detectedTileIds[i] = (Integer) result.get("id");
			detectedTileNames[i] = idToName(detectedTileIds[i]);
			similarities[i] = (Float) result.get("similarity");
		}
		
		long end = System.currentTimeMillis();
		return end - start;
	}
	
	private long orbAdvancedDetection() {
		Log.d("TAG", "Runninng orb Advanced Detection");
		
		long start = System.currentTimeMillis();
		getMainColors();
		
		for (int i=0; i<slicedImages.length; i++) {
			// detect main color
			String mainColor = mainColors[i];
			DescriptorMatcher matcher = null; 
			if (mainColor == "RED") {
				matcher = rMatcher;
			} else {
				matcher = otherMatcher;
			}
			
			Bitmap target = slicedImages[i];
			HashMap<String, Number> result = matchImage(matcher, target);
			
			detectedTileIds[i] = advancedId2NormalId((Integer) result.get("id"), mainColor);
			detectedTileNames[i] = idToName(detectedTileIds[i]);
			similarities[i] = (Float) result.get("similarity");
			
			Log.d("TAG", "detected id:" + String.valueOf(result.get("id")) + "converted:" + String.valueOf(detectedTileIds[i]));
		}
		
		long end = System.currentTimeMillis();
		return end - start;
	}

	private HashMap<String, Number> matchImage(DescriptorMatcher matcher, Bitmap target) {
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
		
		HashMap<String, Number> ret = new HashMap<String, Number>();
		ret.put("id", maxImageId);
		ret.put("similarity", similarity);
		
		return ret;
	}
	
	// detect helpers	
	private double getEuclideanDistance(Bitmap target, Bitmap template) {
		// check and convert resolution
		int width = target.getWidth();
		int height = target.getHeight();
		
		// change to same resolution
		template = effectChangeResolution(template, width, height);
		
		// get pixels
		int[] templatePixels = new int[width * height];
		int[] targetPixels = new int[width * height];
		template.getPixels(templatePixels, 0, width, 0, 0, width, height);
		target.getPixels(targetPixels, 0, width, 0, 0, width, height);
		
		// calculate diff
		int srcColor = 0;
		int targetColor = 0;
		int rDiff = 0;
		int gDiff = 0;
		int bDiff = 0;
		int rDiffSum = 0;
		int gDiffSum = 0;
		int bDiffSum = 0;
		
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				srcColor = templatePixels[x + y*width];
				targetColor = targetPixels[x + y*width];
				
				rDiff = Color.red(srcColor) - Color.red(targetColor);
				rDiffSum += Math.pow(rDiff, 2);
				gDiff = Color.green(srcColor) - Color.green(targetColor);
				gDiffSum += Math.pow(gDiff, 2);
				bDiff = Color.blue(srcColor) - Color.blue(targetColor);
				bDiffSum += Math.pow(bDiff, 2);
			}
		}
		
		double maxDiff, numerator, denominator, difference;
		
		maxDiff = Math.sqrt(width * height * Math.pow(255, 2));
		denominator = 3 * maxDiff;
		numerator = Math.sqrt(rDiffSum) + Math.sqrt(gDiffSum) + Math.sqrt(bDiffSum);
		difference = numerator / denominator * 100;
		
		return difference; 
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
		}
		
		return descriptors;
	}
	
	// getters
	public String getCurrentMethod() {
		String[] methods = {"Euclidean Distance", "ORB Matcher", "Adv. ORB Matcher"};
		return methods[methodType];
	}
	
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
	
	public String[] getPredetectionResult() {
		return predetectionResult;
	}
 	
	// setters
	public void setSourceImage(Bitmap sourceImage) {
		// clear current resources
		recycleSlicedImages();
		for (int i = 0; i < detectedTileIds.length; i++) {
			detectedTileIds[i] = -1;
		}
		for (int i = 0; i < detectedTileNames.length; i++) {
			detectedTileNames[i] = "";
		}
		
		isSourceLoaded = true;
		sliceImage(sourceImage);
	}
	
	public void setMethod(int methodType) {
		// clear current items
		matcher = null;
		rMatcher = null;
		otherMatcher = null;
		
		this.methodType = methodType;
		loadTemplates();
	}
}
