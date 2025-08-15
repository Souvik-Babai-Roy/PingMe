# WhatsApp Clone - Android App

A feature-rich WhatsApp clone built for Android that mimics the core functionality of the original WhatsApp application.

## 🚀 Features

### Core Messaging
- **Real-time messaging** with Firebase Realtime Database
- **Message status indicators** (sent, delivered, read)
- **Message reactions** (👍❤️😂😮😢😡) - Long press any message to react
- **Message editing** (within 15 minutes)
- **Message deletion** (for me/for everyone within 1 hour)
- **Reply to messages** with quote preview
- **Forward messages** to other chats
- **Typing indicators** showing when someone is typing

### Media Support
- **Image sharing** with preview and full-screen viewer
- **Video sharing** with thumbnail previews
- **Audio messages** with recording and playback
- **Document sharing** with file size and type indicators
- **Camera integration** for quick photo/video capture

### User Management
- **User authentication** with Firebase Auth
- **Profile management** with customizable about text
- **Profile photos** with privacy settings
- **Online/offline status** with last seen timestamps
- **Friend system** with add/remove functionality
- **User blocking** and unblocking
- **Privacy settings** (profile photo, last seen, read receipts)

### Chat Features
- **Chat list** with unread message counts
- **Message search** functionality
- **Chat clearing** and deletion
- **Broadcast messages** to multiple contacts
- **Group chat support** (basic implementation)
- **Message backup** and restore

### UI/UX
- **WhatsApp-style design** with green theme
- **Dark/Light theme** support
- **Material Design** components
- **Smooth animations** and transitions
- **Responsive layout** for different screen sizes
- **Accessibility features** with content descriptions

### Notifications
- **Push notifications** for new messages
- **Custom notification sounds**
- **Notification badges** with unread counts
- **Background message processing**

### Security & Privacy
- **End-to-end encryption** (basic implementation)
- **Privacy controls** for profile visibility
- **Secure authentication** with Firebase
- **Data privacy** compliance

## 🛠️ Technical Stack

- **Language**: Java
- **Platform**: Android (API 24+)
- **Backend**: Firebase
  - Firebase Authentication
  - Firebase Realtime Database
  - Firebase Firestore
  - Firebase Cloud Messaging
- **UI Framework**: Android Views with Data Binding
- **Image Loading**: Glide
- **Cloud Storage**: Cloudinary
- **Architecture**: MVVM with Repository pattern

## 📱 Screenshots

The app includes the following main screens:
- **Splash Screen** - App launch with logo
- **Authentication** - Login/Signup with Google
- **Main Activity** - Chats, Status, Calls tabs
- **Chat Activity** - Real-time messaging interface
- **Profile Management** - User profile editing
- **Settings** - App preferences and privacy
- **Image Viewer** - Full-screen media viewing

## 🔧 Setup Instructions

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK API 24+
- Firebase project with Authentication and Realtime Database enabled
- Cloudinary account for media storage

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd whatsapp-clone
   ```

2. **Configure Firebase**
   - Create a Firebase project
   - Enable Authentication (Google Sign-in)
   - Enable Realtime Database
   - Enable Firestore
   - Enable Cloud Messaging
   - Download `google-services.json` and place it in the `app/` directory

3. **Configure Cloudinary**
   - Create a Cloudinary account
   - Update the Cloudinary credentials in `app/build.gradle`:
     ```gradle
     buildConfigField "String", "CLOUDINARY_CLOUD_NAME", '"your_cloud_name"'
     buildConfigField "String", "CLOUDINARY_API_KEY", '"your_api_key"'
     buildConfigField "String", "CLOUDINARY_API_SECRET", '"your_api_secret"'
     ```

4. **Build and Run**
   ```bash
   ./gradlew build
   ./gradlew installDebug
   ```

## 🎯 Key Features Implementation

### Message Reactions
- Long press any message to show reaction picker
- 6 emoji reactions available (👍❤️😂😮😢😡)
- Tap same emoji to remove reaction
- Reactions are displayed below messages

### Real-time Messaging
- Uses Firebase Realtime Database for instant message delivery
- Message status updates in real-time
- Typing indicators show when users are composing messages

### Privacy Features
- Control who can see your profile photo
- Hide last seen timestamps
- Disable read receipts
- Block unwanted users

### Media Handling
- Automatic image/video compression
- Cloudinary integration for media storage
- Thumbnail generation for videos
- Audio recording with waveform display

## 🔒 Privacy & Security

The app implements several privacy and security measures:
- **User data protection** with Firebase security rules
- **Media encryption** for sensitive content
- **Privacy controls** for user visibility
- **Secure authentication** with Google Sign-in
- **Data minimization** - only necessary data is collected

## 🚀 Performance Optimizations

- **Lazy loading** for chat lists and media
- **Image caching** with Glide
- **Message pagination** for large chat histories
- **Background processing** for media uploads
- **Memory management** for large media files

## 📋 TODO Features

- [ ] Voice and video calling
- [ ] Group chat improvements
- [ ] Message backup to cloud
- [ ] Advanced encryption
- [ ] Message scheduling
- [ ] Custom themes
- [ ] Message translation
- [ ] Business features

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## 📄 License

This project is for educational purposes. Please respect WhatsApp's intellectual property rights.

## 🙏 Acknowledgments

- WhatsApp for the original app design and features
- Firebase for the backend services
- Material Design for UI components
- Open source community for various libraries

---

**Note**: This is a clone for educational purposes. The original WhatsApp is a product of Meta Platforms, Inc.