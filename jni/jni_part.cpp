#include <jni.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <opencv2/legacy/legacy.hpp>
#include <vector>
#include <android/log.h>

#define LOG_TAG "JNI_PART"
#define LOGV(...) __android_log_print(ANDROID_LOG_SILENT, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace std;
using namespace cv;

extern "C" {
float THRESHOLD = 45;
int IMAGE_NUM = 0;

OrbFeatureDetector detector(300);
OrbDescriptorExtractor extractor;
BruteForceMatcher<Hamming> matcher;

JNIEXPORT void JNICALL Java_com_astrider_mahjongTileRecognizer_CaptureHelper_setTrainingImages(JNIEnv* env, jobject, jintArray widths, jintArray heights, jobjectArray rgbas, jint imageNum)
{
    LOGV("setTrainingImages");

    jint* _widths = env->GetIntArrayElements(widths, 0);
    jint* _heights = env->GetIntArrayElements(heights, 0);
    jintArray rgba;

    vector<Mat> trainDescriptorses;
    vector<KeyPoint>trainKeypoints;
    Mat trainDescriptors;

    IMAGE_NUM = imageNum;

    //各画像に対し、特徴量を抽出し特徴量照合器(matcher)へ登録
    for(int i = 0; i < imageNum; i++){
       rgba = (jintArray)env->GetObjectArrayElement(rgbas, i);
       jint*  _rgba = env->GetIntArrayElements(rgba, 0);
       Mat mrgba(_heights[i], _widths[i], CV_8UC4, (unsigned char *)_rgba); //ピクセルデータをMatへ変換

       Mat gray(_heights[i], _widths[i], CV_8UC1);
       cvtColor(mrgba, gray, CV_RGBA2GRAY, 0); //グレースケールへ変換

       detector.detect(gray, trainKeypoints); // 特徴点をtrainKeypointsへ格納
       extractor.compute(gray, trainKeypoints, trainDescriptors); //各特徴点の特徴ベクトルをtrainDescriptorsへ格納
       trainDescriptorses.push_back(trainDescriptors);
    }
    matcher.add(trainDescriptorses);//照合器へ全ての学習画像の特徴ベクトルを登録
}

JNIEXPORT jint JNICALL Java_com_astrider_mahjongTileRecognizer_CaptureHelper_detectImage(JNIEnv* env, jobject, jint width, jint height, jbyteArray yuv)
{
    LOGV("detectImage");

    jbyte* _yuv  = env->GetByteArrayElements(yuv, 0);

    vector<KeyPoint> queryKeypoints;
    Mat queryDescriptors;

    Mat myuv(height + height/2, width, CV_8UC1, (unsigned char *)_yuv);
    Mat mgray(height, width, CV_8UC1, (unsigned char *)_yuv);

    detector.detect(mgray, queryKeypoints);
    extractor.compute(mgray, queryKeypoints, queryDescriptors);

    LOGV("start matching");
    // BrustForceMatcher による画像マッチング
    vector<DMatch> matches;
    matcher.match(queryDescriptors, matches);

    int votes[IMAGE_NUM]; // 学習画像の投票箱
    for(int i = 0; i < IMAGE_NUM; i++) votes[i] = 0;

    LOGV("vote");
    // キャプチャ画像の各特徴点に対して、ハミング距離が閾値より小さい特徴点を持つ学習画像へ投票
    for(int i = 0; i < matches.size(); i++){
      if(matches[i].distance < THRESHOLD){
        votes[matches[i].imgIdx]++;
      }
    }

    LOGV("check vote res");
    // 投票数の多い画像のIDを調査
    int maxImageId = -1;
    int maxVotes = 0;
    for(int i = 0; i < IMAGE_NUM; i++){
      if(votes[i] > maxVotes){
        maxImageId = i;  //マッチした特徴点を一番多く持つ学習画像のID
        maxVotes = votes[i]; //マッチした特徴点の数
      }
    }

    vector<Mat> trainDescs = matcher.getTrainDescriptors();

    float similarity = (float)maxVotes/trainDescs[maxImageId].rows*100;
    if(similarity < 5){
      maxImageId = -1; // マッチした特徴点の数が全体の5%より少なければ、未検出とする
    }

    env->ReleaseByteArrayElements(yuv, _yuv, 0);
    return maxImageId;
}

}
