package com.astrider.mahjongTileRecognizer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opencv.android.Utils;
import org.opencv.core.Core;
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
	public static final double CONTRAST_LEVEL = 3;
	
	Resources res;
	String packageName;
	
	boolean isSourceLoaded = false;
	int methodType = METHOD_ORB_ADVANCED;
	
	Bitmap[] templateImages = null;
	DescriptorMatcher matcher = null;
	DescriptorMatcher rMatcher = null;
	DescriptorMatcher gMatcher = null;
	DescriptorMatcher bMatcher = null;
	FeatureDetector detector = FeatureDetector.create(FeatureDetector.ORB);
	DescriptorExtractor extractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
	
	Bitmap[] slicedImages = null;
	int[] detectedTileIds = new int[TILE_NUM];
	String[] detectedTileNames = new String[TILE_NUM];
	String[] predetectionResult = new String[TILE_NUM];
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
	
	// static methods (tools)
	public static Bitmap effectDrawEdges(Bitmap bitmap) {		
		Mat src = new Mat();
		Mat gray = new Mat();
		Mat dst = new Mat();
		Utils.bitmapToMat(bitmap, src);

		int min_x_thresh = (int) (src.rows() * 0.1);
		int max_x_thresh = (int) (src.rows() * 0.9);
		int min_y_thresh = (int) (src.cols() * 0.1);
		int max_y_thresh = (int) (src.cols() * 0.9);
		
		Imgproc.cvtColor(src , gray, Imgproc.COLOR_RGB2GRAY); 
		Imgproc.threshold(gray, dst, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
		Mat hierarchy = new Mat();
		List<MatOfPoint> contours =new ArrayList<MatOfPoint>(100);
		Imgproc.findContours(dst, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE);
		Imgproc.cvtColor(gray , gray, Imgproc.COLOR_GRAY2BGRA,4);
		
//		Imgproc.drawContours(gray, contours, -1, new Scalar(255, 0, 0), 1);
		if(contours.size() > 0) {
			Mat first = contours.get(0);
			double min_x = first.get(0, 0)[0];
			double max_x = first.get(0, 0)[0];
			double min_y = first.get(0, 0)[1];
			double max_y = first.get(0, 0)[1];
			double x, y, tmp[];
			for (int i = 0; i < contours.size(); i++) {
				Mat m = contours.get(i);

				if(m.rows() > 80) {
					for (int j = 0; j < m.rows(); j++) {
						tmp = m.get(j, 0);
						x = tmp[0];
						y = tmp[1];
						Log.d("TAG", String.valueOf(x) + " " + String.valueOf(y));
						
						if(min_x_thresh < x && x < min_x) {
							min_x = x;
						}
						if(max_x < x && x < max_x_thresh ) {
							max_x = x;
						}
						if(min_y_thresh < y && y < min_y) {
							min_y = y;
						}
						if(max_y < y && y < max_y_thresh) {
							max_y = y;
						}
					}
				}
			}
			
			Log.d("TAG", String.valueOf(min_x) + " " + String.valueOf(max_x) + " " + String.valueOf(max_y) + " " + String.valueOf(min_y));
			Mat sub = src.submat(new Rect(new Point(min_x, min_y), new Point(max_x, max_y)));
			Utils.matToBitmap(sub, bitmap);
			
		}
		

		return bitmap;
	}
	
	public static Bitmap effectChopBoundingRect(Bitmap bitmap) {
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
	
	public static Bitmap effectConvertResolution(Bitmap src) {
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
	
	public static int getMainColor(Bitmap bitmap) {
		int[] colorSum = getRGBSum(bitmap);
		
		int tmpVal = 0;
		int maxKey = 0;
		for (int i = 0; i < colorSum.length; i++) {
			if (tmpVal < colorSum[i]) {
				tmpVal = colorSum[i];
				maxKey = i;
			}
		}
		
		int[] colorArray = {Color.RED, Color.GREEN, Color.BLUE};
		return colorArray[maxKey];
	}
	
	public static String idToName(int id) {
		if(id == -1) return "��";
		
		String name = "";
		String[] headers = {"��", "��", "��"};
		String[] words = {"��", "��", "��", "�k", "��", "��"};
		
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
		slicedImages = new Bitmap[TILE_NUM];
		
		float[][] coords = getTileCoords(sourceImage.getWidth(), sourceImage.getHeight());
		
		for (int i = 0; i < coords.length; i++) {
			int x = (int) coords[i][0];
			int y = (int) coords[i][1];
			int width = (int) coords[i][2] - x;
			int height = (int) coords[i][3] - y;
			
			Bitmap slicedImage = Bitmap.createBitmap(sourceImage, x, y, width, height);
			slicedImage = effectChangeContrast(slicedImage, CONTRAST_LEVEL);
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
	
	// helper methods for loading resource
	private void setupEuclidianDistanceTemplates() {
		Log.d("TAG", "setupEuclidianDistanceTemplates");
		int[] ids = {R.drawable.euc_w1, R.drawable.euc_w2, R.drawable.euc_w3, R.drawable.euc_w4, R.drawable.euc_w5, R.drawable.euc_w6, R.drawable.euc_w7, R.drawable.euc_w8, R.drawable.euc_w9,
					R.drawable.euc_p1, R.drawable.euc_p2, R.drawable.euc_p3, R.drawable.euc_p4, R.drawable.euc_p5, R.drawable.euc_p6, R.drawable.euc_p7, R.drawable.euc_p8, R.drawable.euc_p9,
					R.drawable.euc_s1, R.drawable.euc_s2, R.drawable.euc_s3, R.drawable.euc_s4, R.drawable.euc_s5, R.drawable.euc_s6, R.drawable.euc_s7, R.drawable.euc_s8, R.drawable.euc_s9,
					R.drawable.euc_j1, R.drawable.euc_j2, R.drawable.euc_j3, R.drawable.euc_j4, R.drawable.euc_j5, R.drawable.euc_j6};
		templateImages = new Bitmap[ids.length];
		
		for (int i = 0; i<ids.length; i++) {
			Bitmap src = BitmapFactory.decodeResource(res, ids[i]);
			Bitmap converted = effectConvertResolution(src);
			templateImages[i] = converted;
		}
	}
	
	private void setupORBMatcher() {
		Log.d("TAG", "setupORBMatcher");
		matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
		int[] ids = {R.drawable.orb_w1, R.drawable.orb_w2, R.drawable.orb_w3, R.drawable.orb_w4, R.drawable.orb_w5, R.drawable.orb_w6, R.drawable.orb_w7, R.drawable.orb_w8, R.drawable.orb_w9, 
					R.drawable.orb_p1, R.drawable.orb_p2, R.drawable.orb_p3, R.drawable.orb_p4, R.drawable.orb_p5, R.drawable.orb_p6, R.drawable.orb_p7, R.drawable.orb_p8, R.drawable.orb_p9,  
					R.drawable.orb_s1, R.drawable.orb_s2, R.drawable.orb_s3, R.drawable.orb_s4, R.drawable.orb_s5, R.drawable.orb_s6, R.drawable.orb_s7, R.drawable.orb_s8, R.drawable.orb_s9,
					R.drawable.orb_j1, R.drawable.orb_j2, R.drawable.orb_j3, R.drawable.orb_j4, R.drawable.orb_j5, R.drawable.orb_j6};
		List<Mat> descriptors = getDescriptors(ids);
		matcher.add(descriptors);
	}
	
	private void setupORBAdvancedMatchers() {
		Log.d("TAG", "setupORBAdvancedMatcher");
		rMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
		gMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
		bMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
		int[] rIds = {R.drawable.orb_w1, R.drawable.orb_w2, R.drawable.orb_w3, R.drawable.orb_w4, R.drawable.orb_w5, R.drawable.orb_w6, R.drawable.orb_w7, R.drawable.orb_w8, R.drawable.orb_w9, 
					R.drawable.orb_p6, R.drawable.orb_p7, 
					R.drawable.orb_j1, R.drawable.orb_j2, R.drawable.orb_j3, R.drawable.orb_j4, R.drawable.orb_j6};
		
		int[] gIds = {R.drawable.orb_s1, R.drawable.orb_s2, R.drawable.orb_s3, R.drawable.orb_s4, R.drawable.orb_s5, R.drawable.orb_s6, R.drawable.orb_s7, R.drawable.orb_s8, R.drawable.orb_s9,
					R.drawable.orb_j1, R.drawable.orb_j2, R.drawable.orb_j3, R.drawable.orb_j4, R.drawable.orb_j5, };
		
		int[] bIds = {R.drawable.orb_p1, R.drawable.orb_p2, R.drawable.orb_p3, R.drawable.orb_p4, R.drawable.orb_p5, R.drawable.orb_p8, R.drawable.orb_p9,
					R.drawable.orb_j1, R.drawable.orb_j2, R.drawable.orb_j3, R.drawable.orb_j4, };
		
		// array to contain descriptors
		List<Mat> rDescriptors = getDescriptors(rIds);
		List<Mat> gDescriptors = getDescriptors(gIds);
		List<Mat> bDescriptors = getDescriptors(bIds);
		
		// add descriptors to matcher
		rMatcher.add(rDescriptors);
		gMatcher.add(gDescriptors);
		bMatcher.add(bDescriptors);
	}
	
	// detectors
	private void euclideanDistanceDetection() {
		Log.d("TAG", "Runninng euclidean Distance Detection");
		// prepare variables
		double tmpVal = 0;
		double minDiff = 100;
		int id = 0;
		
		// compare target with each templates
		for (int i = 0; i < slicedImages.length; i++) {
			Bitmap target = slicedImages[i];
			for (int j = 0; j < templateImages.length; j++) {
				tmpVal = getEuclideanDistance(templateImages[j], target);
				if(tmpVal < minDiff) {
					minDiff = tmpVal;
					id = j;
				}
			}
			
			detectedTileIds[i] = id;
			detectedTileNames[i] = idToName(detectedTileIds[i]);
			similarities[i] = (float) (100 - minDiff);
		}
	}
	
	private void orbDetection() {
		Log.d("TAG", "Runninng orb Detection");
		for (int i=0; i<slicedImages.length; i++) {
			// load next sliced tile image
			Bitmap target = slicedImages[i];
			
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
//				if(similarity < 5) {
//					maxImageId = -1;
//				}
			}
			
			detectedTileIds[i] = maxImageId;
			detectedTileNames[i] = idToName(detectedTileIds[i]);
			similarities[i] = similarity;
		}
	}
	
	private void orbAdvancedDetection() {
		Log.d("TAG", "Runninng orb Advanced Detection");
		
		for (int i=0; i<slicedImages.length; i++) {
			// load next sliced tile image
			Bitmap target = slicedImages[i];
//			int[] averageColor = getRGBAverage();
//			int mainColor = getMainColor(target, averageColor);
			int mainColor = getMainColor(target);
			
			Log.d("TAG", String.valueOf(mainColor));
			
			DescriptorMatcher matcher = null; 
			switch (mainColor) {
				case Color.RED:
					predetectionResult[i] = "Red";
					matcher = rMatcher;
					break;
					
				case Color.GREEN:
					predetectionResult[i] = "Green";
					matcher = gMatcher;
					break;
					
				case Color.BLUE:
					predetectionResult[i] = "Blue";
					matcher = bMatcher;
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

	// detect helpers
	private double getEuclideanDistance(Bitmap src, Bitmap target) {
		// check and convert resolution
		src = effectConvertResolution(src);
		target = effectConvertResolution(target);
		
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
		double difference = numerator / denominator * 100;
		
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
		String[] methods = {"���[�N���b�h����", "ORB���o��", "�g��ORB���o��"};
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
		if (slicedImages != null) {
			for (Bitmap slicedImage : slicedImages) {
				slicedImage.recycle();
				slicedImage = null;
			}
		}
		
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
		switch (this.methodType) {
			case METHOD_EUCLIDEANDISTANCE:
				for (Bitmap image : templateImages) {
					image.recycle();
					image = null;
				}
				break;
				
			case METHOD_ORB:
				matcher = null;
				break;
				
			case METHOD_ORB_ADVANCED:
				rMatcher = null;
				gMatcher = null;
				bMatcher = null;
				
				break;
	
			default:
				break;
		}
		
		this.methodType = methodType;
		loadTemplates();
	}
	}
