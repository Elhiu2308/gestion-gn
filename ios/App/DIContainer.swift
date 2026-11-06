import Foundation

/// Contenedor de servicios: permite que la capa de vistas (ViewModels) acceda a
/// los servicios sin pasar el `Context` por cada llamada.
///
 /// Es deliberadamente simple: un registro estático de instancias singleton.
/// No se busca IoC complejo, sino código claro y mantenible.
@MainActor
final class ContenedorServicios {
    static let shared = ContenedorServicios()

    private init() {}

    private lazy var _autenticacion = ServicioAutenticacion()
    private lazy var _documentos = ServicioDocumentos()
    private lazy var _expedientes = ServicioExpedientes()
    private lazy var _archivo = ServicioArchivo()
    private lazy var _notificaciones = ServicioNotificaciones()
    private lazy var _configuracion = ServicioConfiguracion()
    private lazy var _qr = ServicioQR()
    private lazy var _biometrico = ServicioAutenticacionBiometrica()

    func autenticacion() -> ServicioAutenticacion { _autenticacion }
    func documentos() -> ServicioDocumentos { _documentos }
    func expedientes() -> ServicioExpedientes { _expedientes }
    func archivo() -> ServicioArchivo { _archivo }
    func notificaciones() -> ServicioNotificaciones { _notificaciones }
    func configuracion() -> ServicioConfiguracion { _configuracion }
    func qr() -> ServicioQR { _qr }
    func biometrico() -> ServicioAutenticacionBiometrica { _biometrico }
}
