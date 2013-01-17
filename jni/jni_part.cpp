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

    //�e�摜�ɑ΂��A�����ʂ𒊏o�������ʏƍ���(matcher)�֓o�^
    for(int i = 0; i < imageNum; i++){
       rgba = (jintArray)env->GetObjectArrayElement(rgbas, i);
       jint*  _rgba = env->GetIntArrayElements(rgba, 0);
       Mat mrgba(_heights[i], _widths[i], CV_8UC4, (unsigned char *)_rgba); //�s�N�Z���f�[�^��Mat�֕ϊ�

       Mat gray(_heights[i], _widths[i], CV_8UC1);
       cvtColor(mrgba, gray, CV_RGBA2GRAY, 0); //�O���[�X�P�[���֕ϊ�

       detector.detect(gray, trainKeypoints); // �����_��trainKeypoints�֊i�[
       extractor.compute(gray, trainKeypoints, trainDescriptors); //�e�����_�̓����x�N�g����trainDescriptors�֊i�[
       trainDescriptorses.push_back(trainDescriptors);
    }
    matcher.add(trainDescriptorses);//�ƍ���֑S�Ă̊w�K�摜�̓����x�N�g����o�^
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
    // BrustForceMatcher �ɂ��摜�}�b�`���O
    vector<DMatch> matches;
    matcher.match(queryDescriptors, matches);

    int votes[IMAGE_NUM]; // �w�K�摜�̓��[��
    for(int i = 0; i < IMAGE_NUM; i++) votes[i] = 0;

    LOGV("vote");
    // �L���v�`���摜�̊e�����_�ɑ΂��āA�n�~���O������臒l��菬���������_�����w�K�摜�֓��[
    for(int i = 0; i < matches.size(); i++){
      if(matches[i].distance < THRESHOLD){
        votes[matches[i].imgIdx]++;
      }
    }

    LOGV("check vote res");
    // ���[���̑����摜��ID�𒲍�
    int maxImageId = -1;
    int maxVotes = 0;
    for(int i = 0; i < IMAGE_NUM; i++){
      if(votes[i] > maxVotes){
        maxImageId = i;  //�}�b�`���������_����ԑ������w�K�摜��ID
        maxVotes = votes[i]; //�}�b�`���������_�̐�
      }
    }

    vector<Mat> trainDescs = matcher.getTrainDescriptors();

    float similarity = (float)maxVotes/trainDescs[maxImageId].rows*100;
    if(similarity < 5){
      maxImageId = -1; // �}�b�`���������_�̐����S�̂�5%��菭�Ȃ���΁A�����o�Ƃ���
    }

    env->ReleaseByteArrayElements(yuv, _yuv, 0);
    return maxImageId;
}

}
