# Centinela — Semana 3

## Despliegue automatizado, explicabilidad y observabilidad


## 1. Alcance de la semana

Esta semana el sistema pasa de ser funcional a ser operable. Se automatiza el despliegue, se contenedoriza la aplicación, se incorpora la verificación documental, se construye el explicador de casos y se instrumenta el pipeline completo para su trazabilidad.

Al cierre de la semana, la célula debe disponer de un sistema desplegado mediante integración continua, con escalado configurado, capaz de generar explicaciones legibles de sus decisiones y de trazar el recorrido individual de una transacción de extremo a extremo.

**Fuera del alcance de esta semana:** orquestación de contenedores con clusters gestionados, modelos de lenguaje generativo, entornos de staging con intercambio de despliegue.


## 2. Requerimientos

### 2.1 Integración y despliegue continuo

Pipeline que traslada el código desde el repositorio hasta la infraestructura desplegada sin intervención manual.

**Etapas requeridas ante cada integración a la rama principal:**

1. Construcción de la aplicación.
2. Ejecución de las pruebas. Un fallo detiene el pipeline.
3. Construcción de las imágenes de contenedor.
4. Publicación de las imágenes en el registro de contenedores.
5. Despliegue.

**Requerimientos:**

- Ninguna etapa del proceso puede requerir intervención manual entre la integración y el sistema en ejecución.
- Las credenciales que utiliza el pipeline para desplegar constituyen secretos y se gestionan como tales. No se admiten en el archivo de configuración del pipeline.
- Existen al menos dos plataformas viables para implementar el pipeline. Seleccionar una y documentar el criterio, indicando qué se obtiene, qué se sacrifica y en qué contexto la decisión sería la contraria.

### 2.2 Contenedores y escalado

Empaquetar la API de ingesta y el motor de scoring como imágenes de contenedor, publicarlas en un registro privado y desplegarlas en una plataforma de contenedores gestionada.

**Requerimientos:**

- **Reglas de escalado configuradas y justificadas.** Documentar la métrica seleccionada — peticiones por segundo, uso de CPU, profundidad de la cola u otra — y su comportamiento esperado ante un pico de carga. Cada métrica produce una respuesta distinta.
- **Demostración del escalado bajo carga.** Generar carga sobre el sistema y evidenciar el aumento y posterior reducción del número de instancias.
- **Optimización de la imagen.** Documentar el tamaño de las imágenes producidas y las medidas aplicadas para reducirlo. Las capas de la imagen conservan los archivos eliminados en capas posteriores; ningún secreto debe incorporarse durante la construcción.
- Utilizar los niveles gratuitos disponibles del registro de contenedores y de la plataforma de ejecución. Documentar sus límites.

### 2.3 Verificación documental

Implementar el flujo de escalamiento de casos con extracción automática de datos del documento de identidad.

**Secuencia:**

1. El analista carga un documento en el contenedor de objetos configurado en la semana 1.
2. Un servicio de reconocimiento documental extrae los datos estructurados del documento: nombre, número de identificación, fechas.
3. Los datos extraídos se adjuntan al caso para su contraste con la información de la cuenta.

**Requerimientos:**

- **Manejo de fallos de extracción.** Un documento ilegible, incompleto, corrupto o de formato inesperado no debe interrumpir el flujo ni dejar el caso en un estado indeterminado. El documento debe quedar en un estado consultable y el analista debe recibir notificación del resultado.
- Utilizar el nivel gratuito del servicio. Verificar que el volumen de procesamiento previsto se mantiene dentro de sus límites.

**Plan alternativo.** Si el informe de cuotas de la semana 1 determinó que el servicio no está disponible en la suscripción, la extracción se implementa mediante una librería de procesamiento documental ejecutada dentro del componente serverless. El requerimiento de manejo de fallos se mantiene sin cambios.

### 2.4 Explicador de casos

Componente que transforma el detalle de las reglas activadas —persistido por el motor de scoring en la semana 2— en una explicación legible dirigida al analista.

**Requerimientos:**

- **Generación determinista mediante plantilla.** No se utilizan modelos de lenguaje generativo.
- **Correspondencia estricta con las reglas activadas.** La explicación debe referirse exclusivamente a las reglas que efectivamente se activaron y a los valores que las activaron. No debe incorporar afirmaciones no respaldadas por el registro del motor.
- **Ejecución asíncrona.** La generación de la explicación ocurre con posterioridad a la apertura del caso. La indisponibilidad del explicador no impide que los casos se abran; estos quedan sin explicación hasta que el componente se restablezca.

**Salida esperada:**

> *Transacción marcada con score 82 (umbral: 60).*
>
> *Se detectaron 3 transacciones de esta cuenta en los últimos 4 minutos, cuando el promedio es de 1 cada 6 horas (+35 puntos).*
>
> *El monto de $4.200.000 supera en 84× el promedio histórico de la cuenta ($50.000) (+30 puntos).*
>
> *La transacción anterior de esta cuenta se originó en Medellín hace 11 minutos; esta se origina en Madrid, a 8.000 km (+17 puntos).*

La imposibilidad de producir una salida de esta naturaleza indica que el motor de scoring no registró información suficiente sobre su propia decisión. La corrección corresponde al motor, no al explicador.

### 2.5 Observabilidad

Instrumentar el sistema de modo que sea posible reconstruir el recorrido completo de una transacción individual, desde su ingreso por la API hasta el cierre de su caso, atravesando la mensajería, el motor de scoring, los almacenes y el explicador.

**El sistema debe permitir responder, en ejecución:**

- Latencia del scoring de una transacción, en promedio y en el percentil superior.
- Tasa de transacciones procesadas por unidad de tiempo.
- Proporción de transacciones marcadas sobre el total procesado.
- Punto exacto de fallo de una transacción que no generó caso.
- Componente de mayor latencia del pipeline.

**Requerimientos:**

- **Traza distribuida individual.** Dado un identificador de transacción, el sistema debe mostrar su recorrido completo con los tiempos de cada etapa. Un panel de métricas agregadas no satisface este requisito.
- **Alerta configurada.** Definir al menos una condición que requiera intervención humana, con justificación del criterio y del umbral seleccionados.
- Utilizar el nivel gratuito de ingesta de telemetría. Documentar su límite y el consumo estimado.

### 2.6 Documentación de cierre

**Documento de decisiones de arquitectura.** Cerrado, cubriendo las tres semanas. Debe incorporar:

- Plataforma de despliegue continuo seleccionada y criterio aplicado.
- Métrica de escalado seleccionada y comportamiento esperado.
- Componente que se satura primero bajo carga y medidas de mitigación adoptadas.
- Modificaciones que la célula introduciría si iniciara el proyecto nuevamente.

**README de despliegue.** Debe permitir que un tercero sin conocimiento previo del proyecto clone el repositorio, ejecute el script de aprovisionamiento, configure los secretos y obtenga el sistema en ejecución.



## 3. Entregables

| # | Entregable | Descripción |
|---|---|---|
| 1 | Pipeline de despliegue continuo | Construcción, pruebas, empaquetado y despliegue automáticos ante integración a la rama principal. |
| 2 | Justificación de la plataforma de CI/CD | Criterio aplicado, contrapartidas y contexto en que la decisión sería la contraria. |
| 3 | Aplicación contenedorizada | Imágenes publicadas en el registro privado y desplegadas. |
| 4 | Reglas de escalado configuradas | Métrica seleccionada, justificación y evidencia de escalado bajo carga. |
| 5 | Reporte de optimización de imágenes | Tamaño resultante y medidas aplicadas. |
| 6 | Flujo de verificación documental | Extracción operativa, con manejo documentado de fallos de extracción. |
| 7 | Explicador de casos | Generación determinista, asíncrona, con correspondencia estricta a las reglas activadas. |
| 8 | Instrumentación del pipeline | Traza distribuida individual, con tiempos por etapa. |
| 9 | Alerta configurada | Condición, umbral y justificación. |
| 10 | Reporte de crédito consumido | Consumo final del proyecto. |
| 11 | Documento de decisiones de arquitectura | Cerrado, cubriendo las tres semanas. |
| 12 | README de despliegue | Verificado por un tercero ajeno a la célula. |



## 4. Sustentación

La célula sustenta el proyecto mediante una demostración en vivo sobre el sistema desplegado. La demostración debe cubrir, sin búsqueda ni preparación intermedia:

1. Una transacción normal que ingresa al sistema y no es marcada.
2. Una transacción fraudulenta que ingresa, es marcada, y genera un caso con su explicación.
3. Evidencia de que el cliente recibió respuesta antes de la conclusión del análisis.
4. El sistema escalando bajo carga generada en el momento.
5. La traza completa de una transacción específica en la herramienta de monitoreo.
6. Una integración a la rama principal que dispara un despliegue automático.
7. El comportamiento del sistema ante un documento ilegible.
8. El comportamiento del sistema con el explicador detenido.

Los puntos 7 y 8 corresponden a escenarios de fallo. Su inclusión es obligatoria.

Se formularán preguntas sobre las decisiones de arquitectura consignadas en el documento correspondiente.


## 5. Criterios de aceptación

**Despliegue**

- [ ] Una integración a la rama principal despliega el sistema sin intervención manual.
- [ ] Una prueba fallida detiene el pipeline antes del despliegue.
- [ ] Las credenciales del pipeline no residen en el repositorio.
- [ ] Las imágenes de contenedor no contienen secretos en ninguna capa.

**Escalado**

- [ ] La carga generada sobre el sistema produce un aumento observable del número de instancias.
- [ ] Al cesar la carga, el número de instancias se reduce.
- [ ] La métrica de escalado seleccionada está justificada por escrito.

**Explicabilidad**

- [ ] Cada caso marcado dispone de una explicación legible.
- [ ] La explicación se corresponde estrictamente con las reglas que se activaron y con los valores que las activaron.
- [ ] Con el explicador detenido, los casos continúan abriéndose. Al restablecerse, las explicaciones pendientes se generan.
- [ ] La generación de la explicación no incrementa la latencia de la ingesta.

**Verificación documental**

- [ ] Un documento válido produce datos extraídos adjuntos al caso.
- [ ] Un documento corrupto o ilegible no interrumpe el flujo. El caso queda en estado consultable y el analista es notificado.

**Observabilidad**

- [ ] Dado un identificador de transacción, se obtiene su traza completa con tiempos por etapa.
- [ ] La alerta configurada se dispara al provocar la condición que la activa.
- [ ] La célula puede identificar el componente de mayor latencia del pipeline a partir de la instrumentación.

**Transversal**

- [ ] No existe ninguna credencial en el código, en el repositorio, en la configuración del pipeline ni en las imágenes de contenedor.
- [ ] La infraestructura completa se reconstruye desde cero siguiendo el README.
- [ ] El crédito consumido al cierre del proyecto es inferior a 60 USD.


## 6. Consideraciones técnicas

**La instrumentación no admite implementación tardía.** Incorporarla al cierre de la semana obliga a intervenir todos los componentes de forma simultánea. Debe aplicarse conforme se integra cada pieza.

**El explicador expone la calidad del registro del motor de scoring.** Si la información persistida en la semana 2 resulta insuficiente, la corrección corresponde al motor. Reprocesar transacciones o reescribir el registro consume tiempo que esta semana no contempla.

**El escalado se verifica bajo carga.** Una configuración de escalado documentada no constituye evidencia de que el escalado ocurra. Debe generarse carga y observarse el comportamiento.

**Una demostración limitada al camino de ejecución exitoso no permite evaluar el sistema.** Los escenarios de fallo —documento ilegible, explicador detenido— forman parte del alcance y de la sustentación.

**El consumo de crédito alcanza su punto máximo en esta semana.** La generación de carga para demostrar el escalado, la construcción repetida de imágenes y la ingesta de telemetría consumen recursos simultáneamente. Dimensionar las pruebas y ejecutar el script de apagado al cierre de cada jornada.

**Si algún componente del alcance base no se encuentra operativo, no debe iniciarse ninguna extensión.** Un sistema completo tiene mayor valor que uno extenso e incompleto.
