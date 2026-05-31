package com.artemobraz.scripts.checkoutseed

object CheckoutSeedConfig {
  const val EMAIL = "aaa@a.a"
  const val PASSWORD = "aaa@a.a"
  const val USER_NAME = "Демо админ"
  const val PROJECT_NAME = "Shoping app"
  const val PROJECT_DESCRIPTION = "Демо-данные мобильного магазина"
  const val APP_VERSION = "1.5.0"

  const val PLATFORM_IOS = "ios"
  const val PLATFORM_ANDROID = "android"

  val PLATFORMS: Set<String> = setOf(PLATFORM_IOS, PLATFORM_ANDROID)

  const val EXPERIMENT_NAME = "Checkout: скролл vs шаги"
  const val EXPERIMENT_DESCRIPTION = "A/B тест UX оплаты в мобильном приложении (версия 1.5.0)"
  const val GROUP_SCROLL_LABEL = "Один экран (скролл)"
  const val GROUP_STEPS_LABEL = "Пошаговые экраны"
  const val EXPERIMENT_NOTE_STARTED = "Exposure — открыта форма checkout"
  const val EXPERIMENT_NOTE_COMPLETED = "Conversion — успешная оплата"

  const val FUNNEL_NAME = "Воронка оплаты"
  const val FUNNEL_DESCRIPTION = "Форма → страницы 1–3 → оплата"
  const val FUNNEL_STEP_STARTED = "Открыта форма checkout"
  const val FUNNEL_STEP_DELIVERY = "Страница 1: доставка"
  const val FUNNEL_STEP_PAYMENT = "Страница 2: оплата"
  const val FUNNEL_STEP_CONFIRMATION = "Страница 3: подтверждение"
  const val FUNNEL_STEP_COMPLETED = "Оплата завершена"

  const val DASHBOARD_ERRORS_NAME = "Ошибки checkout"
  const val DASHBOARD_ERRORS_DESCRIPTION = "Ошибки загрузки страниц и оплаты по платформам"
  const val CHART_PAGE_LOAD_ERRORS_IOS = "Ошибки загрузки — iOS"
  const val CHART_PAGE_LOAD_ERRORS_ANDROID = "Ошибки загрузки — Android"
  const val CHART_PAYMENT_ERRORS_IOS = "Ошибки оплаты — iOS"
  const val CHART_PAYMENT_ERRORS_ANDROID = "Ошибки оплаты — Android"

  const val DASHBOARD_SUCCESS_NAME = "Успешные оплаты"
  const val DASHBOARD_SUCCESS_DESCRIPTION = "Завершённые покупки"
  const val CHART_SUCCESSFUL_CHECKOUT = "Успешный checkout"
}

object CheckoutEventTypes {
  const val STARTED = "checkout_started"
  const val PAGE_VIEWED = "checkout_page_viewed"
  const val DELIVERY_VIEWED = "checkout_delivery_viewed"
  const val PAYMENT_VIEWED = "checkout_payment_viewed"
  const val CONFIRMATION_VIEWED = "checkout_confirmation_viewed"
  const val COMPLETED = "checkout_completed"
  const val PAGE_LOAD_ERROR = "checkout_page_load_error"
  const val PAYMENT_ERROR = "checkout_payment_error"
}
