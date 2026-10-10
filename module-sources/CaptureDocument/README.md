# 🔐 Secure Document Capture

Secure Document Capture is an Android-based secure document acquisition application developed as part of an academic cybersecurity project.

The application is designed to provide a controlled method for capturing sensitive documents directly through a mobile device camera. Instead of allowing users to select existing images from the gallery or file manager, documents must be captured through the application's camera interface.

The application also performs image quality validation before accepting a document and securely stores accepted documents using application-controlled storage and encryption.

---

# 📌 Project Overview

In document collection and verification environments, allowing users or field agents to upload existing images introduces several security concerns.

An existing image may have been:

- Digitally modified
- Previously captured
- Edited using another application
- Downloaded from another source
- Replaced with an incorrect document
- Stored insecurely on the device

This project reduces these risks by providing a controlled document capture process.

The basic concept is:

```text
Physical Document
        ↓
Secure Camera Interface
        ↓
Live Camera Capture
        ↓
Image Quality Validation
        ↓
Photo Review
        ↓
Encrypted Storage
        ↓
Document Metadata
        ↓
Secure Document Access
```

---

# 🎯 Project Objectives

The main objectives of the application are:

- Capture documents directly using the mobile device camera
- Avoid gallery-based document selection
- Avoid file-manager based document selection
- Validate captured image quality
- Detect excessively blurry images
- Detect images with unacceptable brightness
- Allow users to retake poor-quality images
- Allow users to review captured images before accepting them
- Protect sensitive captured documents
- Store documents in application-controlled storage
- Encrypt stored document images
- Maintain basic document metadata
- Provide controlled access to stored documents

---

# 📄 Supported Document Types

The application can be used for capturing documents such as:

- National Identity Card (NIC)
- Driving Licence
- Vehicle registration / CR documents
- Salary slips
- Bank statements
- Utility bills
- Certificates
- Other verification documents

The document type is selected before opening the secure camera interface.

---

# ✨ Current Features

## Camera Capture

The application provides a custom camera interface using the Android **Camera2 API**.

The application does not depend on the device's default camera application for the document capture process.

Current camera functionality includes:

- Live camera preview
- Capture button
- Back navigation
- Document placement guide
- Image capture
- Image review
- Retake option
- Use Photo option

---

## No Gallery Upload

The application is designed around live document capture.

There is no normal workflow for selecting a previously stored document from:

- Gallery
- Photos application
- File manager
- Downloads folder

This helps maintain better control over how the document enters the application.

---

# 🛠️ Technology Stack

| Technology | Purpose |
|---|---|
| Kotlin | Main Android application programming language |
| Android Studio | Development environment |
| Android Camera2 API | Direct camera control |
| OpenCV 4.10.0 | Image processing and quality validation |
| Android Application Storage | Controlled document storage |
| Android Cryptographic APIs | Protection of stored document data |
| Gradle / Kotlin DSL | Project build and dependency management |

---

# ⚙️ Android Configuration

The current project is configured with:

```text
Language: Kotlin
Build System: Gradle Kotlin DSL
Minimum SDK: API 26
Target SDK: API 36
Compile SDK: API 36
```

The application therefore targets modern Android versions while maintaining support from Android API 26 onward.

---

# 📷 Camera2 API Implementation

The project uses the Android **Camera2 API** instead of CameraX.

Camera2 provides lower-level access to the Android camera hardware and allows the application to directly control the capture workflow.

The camera architecture is approximately:

```text
CaptureActivity
      ↓
CameraManager
      ↓
CameraDevice
      ↓
CameraCaptureSession
      ↓
TextureView
      ↓
Live Camera Preview
```

For still image capture:

```text
Capture Button
      ↓
CaptureRequest
      ↓
CameraCaptureSession
      ↓
ImageReader
      ↓
JPEG Image
      ↓
ByteArray
      ↓
Bitmap
```

---

# 🎥 Camera Preview

A `TextureView` is used to display the live camera preview.

The camera device creates a repeating preview request and sends the camera frames to the `TextureView` surface.

The basic process is:

```text
CameraDevice
     ↓
CaptureRequest
     ↓
CameraCaptureSession
     ↓
Surface
     ↓
TextureView
```

---

# 📸 Image Capture

Captured images are received through Android's `ImageReader`.

The current ImageReader configuration uses JPEG images:

```text
Resolution: 1920 × 1080
Format: JPEG
Maximum Images: 1
```

After a document is captured:

```text
ImageReader
    ↓
Image
    ↓
ByteBuffer
    ↓
ByteArray
    ↓
BitmapFactory
    ↓
Bitmap
```

The resulting bitmap is then passed to the image quality checking component.

---

# 🔄 Image Orientation Handling

The application retrieves the camera sensor orientation using:

```text
CameraCharacteristics.SENSOR_ORIENTATION
```

It also reads the device display rotation.

The application calculates the required JPEG orientation before creating the final capture request.

This helps maintain the correct orientation of captured document images.

---

# 🔍 Image Quality Validation

Before a document is accepted, the application performs image quality checks using OpenCV.

Currently implemented checks are:

1. Blur detection
2. Brightness detection

The image is accepted only when both checks pass.

```text
Captured Bitmap
       ↓
Blur Detection
       ↓
Brightness Detection
       ↓
 ┌─────┴─────┐
Pass         Fail
 ↓             ↓
Review      Recapture
```

---

# 🔎 Blur Detection

Blur detection is implemented using the **variance of the Laplacian**.

The captured bitmap is first converted into an OpenCV `Mat`.

The image is then converted to grayscale.

```text
Captured Bitmap
      ↓
OpenCV Mat
      ↓
Grayscale Image
      ↓
Laplacian Operator
      ↓
Standard Deviation
      ↓
Variance
      ↓
Compare with Threshold
```

The principle is:

```text
Higher Laplacian variance
        =
More edge/detail variation
        =
Sharper image
```

A very low variance can indicate that the captured document is blurry.

The application logs the calculated Laplacian variance during testing, allowing the threshold to be calibrated for the target device.

---

# ☀️ Brightness Detection

Brightness validation is also performed using OpenCV.

The process is:

```text
Bitmap
   ↓
OpenCV Mat
   ↓
Grayscale
   ↓
Calculate Mean Pixel Intensity
   ↓
Compare with Acceptable Range
```

Grayscale pixel values generally range from:

```text
0   = Black
255 = White
```

The application calculates the average grayscale intensity of the captured image.

If the average value is outside the configured acceptable range, the document is rejected and the user is asked to recapture it.

---

# ⚠️ Quality Validation Messages

The application can reject a capture for different reasons.

Examples include:

```text
Image is too blurry. Please recapture.
```

```text
Image brightness is not acceptable. Please recapture.
```

or when both checks fail:

```text
Image is blurry and brightness is not acceptable. Please recapture.
```

This prevents obviously poor-quality images from immediately entering secure storage.

---

# 🖼️ Photo Review

When both image quality checks pass, the captured image is temporarily kept in memory.

The application then opens the review interface.

The user can choose:

```text
Retake
```

or:

```text
Use Photo
```

Selecting **Retake** removes the current temporary capture and returns the user to the camera.

Selecting **Use Photo** continues to secure storage.

---

# 🔐 Secure Storage

Accepted documents are passed to the application's secure storage component.

The storage process is:

```text
Accepted Image
      ↓
Captured Image Bytes
      ↓
SecureStorage
      ↓
Encryption
      ↓
Application-Controlled Storage
```

The objective is to avoid unnecessarily exposing sensitive captured documents through normal shared media storage.

---

# 🗂️ Application-Private Storage

Sensitive document images should not be placed in publicly accessible locations such as:

```text
Pictures/
DCIM/
Downloads/
```

The application instead uses application-controlled storage.

This provides better isolation because other normal applications should not directly access the application's private files.

---

# 🔑 Encryption

Captured document images are encrypted before being stored.

The encryption layer is handled by:

```text
SecureStorage.kt
```

The high-level process is:

```text
Captured Document
       ↓
ByteArray
       ↓
Encryption
       ↓
Encrypted Document
       ↓
Private Storage
```

This provides protection for sensitive document data at rest.

---

# 📝 Document Metadata

The application also stores metadata associated with captured documents.

Examples include:

- Document type
- Generated document/file identifier
- Capture/storage timestamp

The filename is generated using the selected document type and timestamp.

Conceptually:

```text
Document Type
      +
Timestamp
      ↓
Unique File Name
```

This allows stored documents to be identified and organized without relying only on the raw image.

---

# 🔒 Security Architecture

The overall security-focused workflow is:

```text
┌─────────────────────────┐
│ Select Document Type    │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ Controlled Camera       │
│ Camera2 API             │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ Live Document Capture   │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ Blur Detection          │
│ OpenCV                  │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ Brightness Validation   │
│ OpenCV                  │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ User Review             │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ SecureStorage           │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ Encryption              │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ Private Storage         │
└─────────────────────────┘
```

---

# 🛡️ Security Controls

The application currently applies several security-focused controls.

### Controlled Image Acquisition

Documents are captured directly using the application's Camera2 implementation instead of being selected through a gallery workflow.

### Image Quality Validation

OpenCV checks the captured image before it is accepted.

### Application-Controlled Storage

Captured documents are kept within storage controlled by the application.

### Encryption at Rest

Document images are encrypted before permanent storage.

### Temporary Memory Handling

The accepted image remains temporarily available for review and is cleared from the application's temporary reference after secure storage.

### Controlled Document Access

Stored documents are accessed through the application's document interface rather than through the normal gallery workflow.

---

# 🧩 Main Application Components

The project is separated into different components.

## `MainActivity.kt`

Main entry point of the application.

---

## `DocumentSelectionActivity.kt`

Allows the user to select the type of document that will be captured.

The selected document type is passed to the capture activity.

---

## `CaptureActivity.kt`

Responsible for the main secure capture workflow.

Responsibilities include:

- Camera permission handling
- Camera2 initialization
- Live camera preview
- Image capture
- ImageReader handling
- JPEG orientation
- Blur validation
- Brightness validation
- Photo review
- Retake workflow
- Passing accepted images to secure storage

---

## `DocumentQualityChecker.kt`

Responsible for image quality validation.

Currently contains:

```text
isSharpEnough()
isBrightnessAcceptable()
```

OpenCV is used for both checks.

---

## `OpenCVInitializer.kt`

Responsible for initializing the OpenCV library before OpenCV-based image processing functions are used.

---

## `SecureStorage.kt`

Responsible for protecting and storing captured document data.

Its responsibilities include:

- Image encryption
- Secure image storage
- Document metadata storage

---

## `SecureDocumentsActivity.kt`

Provides the interface for accessing documents that have been securely stored by the application.

---

# 📁 Simplified Project Structure

```text
SecureDocumentCapture3/
│
├── app/
│   ├── src/
│   │   └── main/
│   │       │
│   │       ├── java/com/example/securedocumentcapture3/
│   │       │   ├── MainActivity.kt
│   │       │   ├── DocumentSelectionActivity.kt
│   │       │   ├── CaptureActivity.kt
│   │       │   ├── DocumentQualityChecker.kt
│   │       │   ├── OpenCVInitializer.kt
│   │       │   ├── SecureStorage.kt
│   │       │   └── SecureDocumentsActivity.kt
│   │       │
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   ├── drawable/
│   │       │   ├── mipmap/
│   │       │   └── values/
│   │       │
│   │       └── AndroidManifest.xml
│   │
│   └── build.gradle.kts
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
└── README.md
```

---

# 🔄 Complete Application Workflow

```text
START
  ↓
Open Application
  ↓
Select Document Type
  ↓
Open Secure Camera
  ↓
Camera Permission Check
  ↓
Start Camera2 Preview
  ↓
Position Physical Document
  ↓
Capture Photo
  ↓
Create JPEG
  ↓
Decode JPEG to Bitmap
  ↓
OpenCV Blur Check
  ↓
OpenCV Brightness Check
  ↓
        ┌───────────────┐
        │ Quality Good? │
        └───────┬───────┘
                │
          ┌─────┴─────┐
          │           │
         YES          NO
          │           │
          ↓           ↓
    Photo Review   Reject Image
          │           │
     ┌────┴────┐      ↓
     │         │    Recapture
   Retake   Use Photo
     │         │
     ↓         ↓
   Camera   Encryption
               ↓
         Secure Storage
               ↓
        Save Metadata
               ↓
        Storage Success
```

---

# 🔐 Security Design Principles

The application follows several basic secure design principles.

## Least Exposure

Sensitive document images should only be exposed to components that require access.

## Data Protection at Rest

Stored document images are encrypted.

## Controlled Input

Documents enter the application through the controlled camera workflow rather than through arbitrary file selection.

## Fail-Safe Image Validation

If image processing fails, the quality-checking component can reject the document rather than automatically accepting an unchecked image.

## Data Minimization

Only information required for the document capture and storage process should be maintained.

---

# 🚫 What the Application Does Not Currently Do

The current version does **not** perform:

- AI-based document recognition
- OCR-based identity extraction
- Facial recognition
- Automatic fraud classification
- Automatic document boundary detection
- Automatic document cropping
- Perspective correction

These functions should not be assumed to exist in the current implementation.

---

# 🚧 Planned Improvements

Future versions may include:

### Automatic Document Boundary Detection

Detect the physical boundary of the document using traditional image-processing techniques.

### Automatic Cropping

Automatically remove unnecessary background surrounding the document.

### Four-Corner Detection

Identify the four corners of a document.

### Perspective Correction

Transform angled document captures into a flat rectangular representation.

### Document Alignment Validation

Check whether the entire document is positioned correctly inside the capture guide.

### Improved Quality Calibration

Calibrate blur and brightness thresholds using measurements collected from target mobile devices.

### Additional Security Hardening

Further security controls can be introduced depending on the deployment environment.

---

# 🧪 Testing

The application should be tested under different capture conditions.

Recommended tests include:

| Test | Expected Result |
|---|---|
| Clear document | Accepted |
| Blurry document | Rejected |
| Very dark image | Rejected |
| Excessively bright image | Rejected |
| Valid image + Retake | Return to camera |
| Valid image + Use Photo | Securely store document |
| Gallery upload attempt | No gallery capture workflow available |
| Camera permission denied | Capture prevented |

Blur and brightness thresholds should be calibrated using actual values recorded from the target device rather than assuming one threshold works equally well for every camera.

---

# 📊 OpenCV Debugging

During development, image quality measurements are logged using:

```text
QUALITY_CHECK
```

Example values include:

```text
Laplacian variance = ...
Average brightness = ...
```

These values can be viewed through Android Studio Logcat.

They are useful for determining appropriate image quality thresholds for the target device.

---

# 🖥️ Development Environment

Recommended development environment:

```text
Android Studio
Kotlin
Gradle Kotlin DSL
Android SDK 36
OpenCV 4.10.0
```

A physical Android device is recommended for final camera testing because camera behavior and image quality can differ significantly from an emulator.

---

# 🚀 Building the Project

## 1. Clone the Repository

```bash
git clone <repository-url>
```

## 2. Open Android Studio

Select:

```text
Open
```

and select the cloned project folder.

## 3. Allow Gradle Sync

Wait until Android Studio finishes downloading and configuring the required dependencies.

## 4. Connect an Android Device

Enable Developer Options and USB Debugging on the test device.

## 5. Run the Application

Select the connected Android device and press:

```text
Run ▶
```

## 6. Grant Camera Permission

The application requires camera permission for document capture.

---

# 🔧 OpenCV Dependency

The project uses OpenCV for image quality analysis.

The project currently uses:

```gradle
implementation("org.opencv:opencv:4.10.0")
```

OpenCV must initialize successfully before blur and brightness analysis can operate.

---

# 🔏 Required Android Permission

The camera feature requires:

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

The application requests runtime camera permission when required.

---

# ⚠️ Current Limitations

This project is currently an academic prototype.

Current limitations include:

- Image quality thresholds may require calibration for different devices
- Camera behavior may differ between Android manufacturers
- Automatic document cropping is not currently implemented
- Automatic boundary detection is not currently implemented
- Perspective correction is not currently implemented
- Additional security testing is required before production deployment
- Additional privacy and compliance requirements would need to be evaluated for real-world sensitive document processing

---

# 🔮 Future Capture Pipeline

A future version can extend the current implementation as follows:

```text
Camera Capture
      ↓
Blur Check
      ↓
Brightness Check
      ↓
Document Boundary Detection
      ↓
Four-Corner Detection
      ↓
Perspective Correction
      ↓
Automatic Crop
      ↓
Review
      ↓
Encryption
      ↓
Secure Storage
```

The document boundary detection and cropping stages are intentionally separated from the current quality-validation implementation so they can be developed and tested independently.

---

# 🎓 Academic Purpose

This application was developed for academic purposes as part of a cybersecurity project.

The project demonstrates practical concepts related to:

- Secure mobile application development
- Android Camera2 API
- Image processing
- OpenCV
- Secure document acquisition
- Data protection
- Encryption
- Application-private storage
- Secure software design

---

# ⚠️ Disclaimer

This project is an academic prototype and should not be considered a production-ready identity verification or document verification system.

A real-world deployment would require additional:

- Security testing
- Privacy assessment
- Threat modelling
- Device compatibility testing
- Cryptographic review
- Secure key management review
- Access-control testing
- Data retention policies
- Regulatory and compliance assessment
- Production hardening

---

# 📌 Current Development Status

### Implemented

- ✅ Kotlin Android application
- ✅ Camera2 API
- ✅ Live camera preview
- ✅ Direct document capture
- ✅ Document type selection
- ✅ OpenCV integration
- ✅ Blur detection
- ✅ Brightness validation
- ✅ Quality-based recapture
- ✅ Photo review
- ✅ Retake functionality
- ✅ Secure storage component
- ✅ Document encryption
- ✅ Metadata storage
- ✅ Stored document interface

### Future Work

- ⏳ Document boundary detection
- ⏳ Automatic document cropping
- ⏳ Four-corner detection
- ⏳ Perspective correction
- ⏳ Alignment validation
- ⏳ Further device-specific quality calibration

---

# 📜 License

This repository was created primarily for educational and academic purposes.

Please contact the project authors before reusing project-specific implementation or academic material.
