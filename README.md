# Dorm Room Manager

A simple Android application to help manage dorm rooms, tenants, and their bills.

## Features

*   **Property Management**: Add and manage multiple properties (e.g., dorm buildings).
*   **Room Management**: For each property, add and manage individual rooms, including details like rent, electricity rates, and initial meter readings.
*   **Tenant Management**:
    *   Assign tenants to rooms.
    *   Record tenant move-in and move-out dates.
    *   Store tenant contact information.
*   **Billing**:
    *   Generate monthly bills for rooms based on rent and electricity usage (calculated from meter readings).
    *   Record bill payments.
    *   View bill history for each room.
*   **Global Search**: working on it
*   **Data Persistence**: Uses Room database to store all application data locally on the device.

## Technologies Used

*   **Kotlin**: Primary programming language.
*   **Jetpack Compose**: For building the user interface.
*   **Jetpack Room**: For local database storage.
*   **Jetpack ViewModel**: To manage UI-related data in a lifecycle-conscious way.
*   **Jetpack Navigation**: To handle in-app navigation.
*   **Hilt**: For dependency injection.
*   **Material Design 3**: For UI components and styling.

## Getting Started

1.  Clone the repository:
    ```bash
    git clone [Your GitHub Repository URL Here]
    ```
2.  Open the project in Android Studio (latest stable version recommended).
3.  Let Android Studio download the necessary Gradle dependencies.
4.  Build and run the application on an Android emulator or a physical device.

## Future Enhancements (Examples)

*   Cloud backup and sync.
*   Reporting and analytics.
*   Receipt generation (PDF).
*   User authentication.
*   Reminders for bill payments.

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.

## License

[MIT] <!-- Or choose another license if you prefer -->