package com.agendacomercial.app

data class BudgetCatalogProduct(
    val id: String,
    val name: String,
    val unitsPerBox: Int,
    val description: String,
    val category: String,
    val webUrl: String
)

val PaSolaBudgetCatalog = listOf(
    BudgetCatalogProduct(
        id = "hamburguesa_de_luxe_80",
        name = "Hamburguesa de luxe 80 g",
        unitsPerBox = 30,
        description = "Pan de hamburguesa esponjoso elaborado con mantequilla y leche.",
        category = "Bocadillos · Hamburguesas",
        webUrl = "https://pasolasl.com/tienda/bocadillos/hamburguesas/hamburguesa-de-luxe/"
    ),
    BudgetCatalogProduct(
        id = "hamburguesa_sesamo_80",
        name = "Hamburguesa sésamo 80 g",
        unitsPerBox = 30,
        description = "Formato redondo clásico con topping de sésamo tostado.",
        category = "Bocadillos · Hamburguesas",
        webUrl = "https://pasolasl.com/categoria/bocadillos/"
    ),
    BudgetCatalogProduct(
        id = "hamburguesa_sin_topping_80",
        name = "Hamburguesa sin topping 80 g",
        unitsPerBox = 30,
        description = "Pan de hamburguesa clásico en formato redondo.",
        category = "Bocadillos · Hamburguesas",
        webUrl = "https://pasolasl.com/categoria/bocadillos/"
    ),
    BudgetCatalogProduct(
        id = "hamburguesa_cereales_80",
        name = "Hamburguesa cereales 80 g",
        unitsPerBox = 30,
        description = "Pan esponjoso con cereales en su interior.",
        category = "Bocadillos · Hamburguesas",
        webUrl = "https://pasolasl.com/categoria/bocadillos/"
    ),
    BudgetCatalogProduct(
        id = "hamburguesa_pimientas_80",
        name = "Hamburguesa pimientas 80 g",
        unitsPerBox = 30,
        description = "Pan esponjoso con tres tipos de pimientas.",
        category = "Bocadillos · Hamburguesas",
        webUrl = "https://pasolasl.com/categoria/bocadillos/"
    ),
    BudgetCatalogProduct(
        id = "hamburguesa_curry_80",
        name = "Hamburguesa curry 80 g",
        unitsPerBox = 30,
        description = "Pan de hamburguesa esponjoso con curry.",
        category = "Bocadillos · Hamburguesas",
        webUrl = "https://pasolasl.com/categoria/bocadillos/"
    ),
    BudgetCatalogProduct(
        id = "hamburguesa_cristal",
        name = "Hamburguesa cristal",
        unitsPerBox = 40,
        description = "Pan de hamburguesa de textura crujiente y ligera.",
        category = "Bocadillos · Hamburguesas",
        webUrl = "https://pasolasl.com/categoria/bocadillos/"
    ),
    BudgetCatalogProduct(
        id = "frankfurt_80",
        name = "Frankfurt 80 g",
        unitsPerBox = 50,
        description = "Pan clásico para frankfurt.",
        category = "Bocadillos · Hot dog",
        webUrl = "https://pasolasl.com/categoria/bocadillos/"
    ),
    BudgetCatalogProduct(
        id = "frankfurt_de_luxe_80",
        name = "Frankfurt de luxe 80 g",
        unitsPerBox = 30,
        description = "Pan de frankfurt dulce y esponjoso.",
        category = "Bocadillos · Hot dog",
        webUrl = "https://pasolasl.com/categoria/bocadillos/"
    ),
    BudgetCatalogProduct(
        id = "pan_bao_60",
        name = "Pan bao 60 g",
        unitsPerBox = 60,
        description = "Pan bao de estilo taiwanés, pensado para formato hot dog.",
        category = "Bocadillos · Bao",
        webUrl = "https://pasolasl.com/categoria/bocadillos/"
    ),
    BudgetCatalogProduct(
        id = "focaccia_90",
        name = "Focaccia 90 g",
        unitsPerBox = 40,
        description = "Versión Pa Solà del clásico pan italiano.",
        category = "Bocadillos · Focaccia",
        webUrl = "https://pasolasl.com/categoria/bocadillos/"
    ),
    BudgetCatalogProduct(
        id = "bagel_100",
        name = "Bagel 100 g",
        unitsPerBox = 36,
        description = "Bagel de miga esponjosa.",
        category = "Bocadillos · Bagels",
        webUrl = "https://pasolasl.com/categoria/bocadillos/"
    ),
    BudgetCatalogProduct(
        id = "flauta_80",
        name = "Flauta 80 g",
        unitsPerBox = 50,
        description = "Formato individual de corteza fina y crujiente.",
        category = "Bocadillos · Flautas",
        webUrl = "https://pasolasl.com/categoria/bocadillos/"
    ),
    BudgetCatalogProduct(
        id = "baguette_catalana_250",
        name = "Baguette catalana 250 g",
        unitsPerBox = 15,
        description = "Baguette pensada para rebanadas o bocadillos.",
        category = "Bocadillos · Barras",
        webUrl = "https://pasolasl.com/categoria/bocadillos/"
    ),
    BudgetCatalogProduct(
        id = "barrot_blanco_500",
        name = "Barrot blanco 500 g",
        unitsPerBox = 15,
        description = "El clásico Barrot de Pa Solà, fermentado lentamente y de elaboración artesanal.",
        category = "Barrots",
        webUrl = "https://pasolasl.com/categoria/barrots/"
    ),
    BudgetCatalogProduct(
        id = "barrot_cereales_500",
        name = "Barrot cereales con semillas 500 g",
        unitsPerBox = 15,
        description = "Barrot con semillas y cereales en su interior.",
        category = "Barrots",
        webUrl = "https://pasolasl.com/categoria/barrots/"
    ),
    BudgetCatalogProduct(
        id = "panecillo_viena_30",
        name = "Panecillo Viena 30 g",
        unitsPerBox = 100,
        description = "Panecillo de textura muy esponjosa.",
        category = "Panecillos",
        webUrl = "https://pasolasl.com/categoria/panecillos/"
    ),
    BudgetCatalogProduct(
        id = "panecillo_pas_nueces_30",
        name = "Panecillo pasas y nueces 30 g",
        unitsPerBox = 100,
        description = "Panecillo con pasas y nueces.",
        category = "Panecillos",
        webUrl = "https://pasolasl.com/categoria/panecillos/"
    ),
    BudgetCatalogProduct(
        id = "molde_brioche_900",
        name = "Molde brioche 900 g",
        unitsPerBox = 6,
        description = "Molde brioche de perfil dulce y textura de repostería.",
        category = "Moldes",
        webUrl = "https://pasolasl.com/categoria/moldes/"
    ),
    BudgetCatalogProduct(
        id = "molde_pas_nueces_900",
        name = "Molde pasas y nueces 900 g",
        unitsPerBox = 6,
        description = "Molde energético y ligeramente dulce con pasas y nueces.",
        category = "Moldes",
        webUrl = "https://pasolasl.com/categoria/moldes/"
    ),
    BudgetCatalogProduct(
        id = "pan_cristal_300",
        name = "Pan de cristal 300 g",
        unitsPerBox = 25,
        description = "Pan de cristal de alta hidratación, cortado a mano una vez fermentado.",
        category = "Cocas",
        webUrl = "https://pasolasl.com/tienda/cocas/cocas-cocas/pan-de-cristal/"
    ),
    BudgetCatalogProduct(
        id = "base_pizza",
        name = "Base pizza",
        unitsPerBox = 20,
        description = "Base crujiente diseñada para mantener su textura después del horneado.",
        category = "Placa / base",
        webUrl = "https://pasolasl.com/categoria/placa-base/"
    ),
    BudgetCatalogProduct(
        id = "remini_de_luxe_13",
        name = "Remini de luxe 13 g",
        unitsPerBox = 150,
        description = "Pieza dulce de pequeño formato de la gama Remini.",
        category = "Reminis",
        webUrl = "https://pasolasl.com/categoria/reminis/"
    ),
    BudgetCatalogProduct(
        id = "airbag_5x5",
        name = "Airbag 5×5",
        unitsPerBox = 100,
        description = "Snack de aire envuelto en una capa muy fina de pan.",
        category = "Snacks",
        webUrl = "https://pasolasl.com/categoria/snacks/"
    ),
    BudgetCatalogProduct(
        id = "crackers",
        name = "Crackers",
        unitsPerBox = 100,
        description = "Crackers tipo regañá de Pa Solà.",
        category = "Snacks",
        webUrl = "https://pasolasl.com/categoria/snacks/"
    )
)

data class BudgetLine(
    val product: BudgetCatalogProduct,
    val code: String,
    val boxes: Int,
    val pricePerBox: Double
)
