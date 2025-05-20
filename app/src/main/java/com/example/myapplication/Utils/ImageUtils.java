package com.example.myapplication.Utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ImageUtils {
    public static Bitmap mediaImgToBitmap(Image image,int rotation,Rect boundingBox) {
        Bitmap frame_bmp = imageToBitmap(image);

        Bitmap frame_bmp1 = rotateBitmap(frame_bmp,rotation);

        float padding = 0.0f;

        RectF adjustedBoundingBox = new RectF(
                boundingBox.left - padding,
                boundingBox.top - padding,
                boundingBox.right + padding,
                boundingBox.bottom + padding);

        Bitmap cropped_face = getCropBitmapByCPU(frame_bmp1, adjustedBoundingBox);

        return resizedBitmap(cropped_face);

    }
    public static Bitmap imageToBitmap(Image image) {
        byte[] nv21 = YUV_420_888toNV21(image);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(),null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0,0,yuvImage.getHeight(),yuvImage.getHeight()),75,out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length);
    }


    // Chuyển định dạng ảnh từ YUV_420_888 sang NV21
    private static byte[] YUV_420_888toNV21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width * height;
        int uvSize = width*height/4;

        byte[] nv21 = new byte[ySize + uvSize *2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); //Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        int yRowStride = image.getPlanes()[0].getRowStride();
        int uvRowStride = image.getPlanes()[1].getRowStride();
        int uvPixelStride = image.getPlanes()[1].getPixelStride();

        // Sao chép Y
        int pos = 0;
        for (int row = 0; row<height; row++) {
            yBuffer.position(row*yRowStride);
            yBuffer.get(nv21,pos,width);
            pos += width;
        }

        int uvHeight = height/2;
        for (int row=0; row < uvHeight; row++) {
            for (int col=0; col< width/2; col++) {
                int uIndex = row*uvRowStride + col*uvPixelStride;
                int vIndex = row*uvRowStride + col*uvPixelStride;

                nv21[pos++] = vBuffer.get(vIndex);
                nv21[pos++] = uBuffer.get(uIndex);
            }
        }

        return nv21;
    }

    // Xoay ảnh về đúng chiều
    public static Bitmap rotateBitmap(Bitmap bitmap,int rotationDegrees) {
        if (rotationDegrees == 0) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);

        Bitmap rotatedBitmap = Bitmap.createBitmap(
                bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true
        );

        if (rotatedBitmap != null) {
            bitmap.recycle();
        }

        return rotatedBitmap;
    }


    // Crop ảnh để lấy đúng phần mặt cần phân tích
    public static Bitmap getCropBitmapByCPU(Bitmap bitmap, RectF rectF) {
        Bitmap resultBitmap = Bitmap.createBitmap((int) rectF.width(),(int) rectF.height(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(resultBitmap);

        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);
        canvas.drawRect(
                new RectF(0,0,rectF.width(),rectF.height()),paint
        );

        Matrix matrix = new Matrix();
        matrix.postTranslate(-rectF.left,-rectF.top);

        canvas.drawBitmap(bitmap, matrix, paint);

        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }

        return resultBitmap;
    }

    // Resize ảnh về 112x112 (kích thước tiêu chuẩn cho các mô hình học máy)
    public static Bitmap resizedBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleWidth = ((float) 112) / width;
        float scaleHeight = ((float) 112) / height;

        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth,scaleHeight);

        Bitmap finalBitmap = Bitmap.createBitmap(bitmap,0,0,width,height,matrix,false);

        if (bitmap != null) {
            bitmap.recycle();
        }

        return finalBitmap;
    }


}