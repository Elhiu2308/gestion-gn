import Foundation

struct AIMessage: Identifiable, Codable {
    let id: UUID
    let role: AIRole
    let content: String
    let timestamp: Date

    init(role: AIRole, content: String) {
        self.id = UUID()
        self.role = role
        self.content = content
        self.timestamp = Date()
    }
}

enum AIRole: String, Codable {
    case system, user, assistant
}
