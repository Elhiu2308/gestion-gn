import SwiftUI

/// Vista de chat con el agente de IA gratuito y local (Ollama).
struct AIChatView: View {
    @StateObject private var viewModel = AIChatViewModel()

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    ScrollViewReader { proxy in
                        LazyVStack(spacing: 12) {
                            ForEach(viewModel.messages) { msg in
                                if msg.role != .system {
                                    AIMessageBubble(message: msg)
                                        .id(msg.id)
                                }
                            }
                        }
                        .padding()
                        .onChange(of: viewModel.messages.count) {
                            if let last = viewModel.messages.last(where: { $0.role != .system }) {
                                withAnimation {
                                    proxy.scrollTo(last.id, anchor: .bottom)
                                }
                            }
                        }
                    }
                }

                if viewModel.isLoading {
                    HStack {
                        ProgressView()
                        Text("Escribiendo...")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 8)
                }

                if let error = viewModel.error {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                        .padding(.horizontal)
                }

                HStack(spacing: 8) {
                    TextField("Escribe tu mensaje...", text: $viewModel.inputText)
                        .textFieldStyle(.roundedBorder)
                        .onSubmit {
                            Task { await viewModel.send() }
                        }

                    Button {
                        Task { await viewModel.send() }
                    } label: {
                        Image(systemName: "paperplane.fill")
                    }
                    .disabled(viewModel.inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.isLoading)
                }
                .padding()
            }
            .navigationTitle("GestióN Documental")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button("llama3.2") { Task { await changeModel(to: "llama3.2") } }
                        Button("codellama") { Task { await changeModel(to: "codellama") } }
                        Button("deepseek-coder") { Task { await changeModel(to: "deepseek-coder") } }
                    } label: {
                        Image(systemName: "gear")
                    }
                }
            }
        }
    }

    @MainActor
    private func changeModel(to name: String) async {
        await OllamaAIService.shared.setModel(name)
        viewModel.messages.removeAll(where: { $0.role == .system })
        viewModel.addSystemMessage("Modelo activo: \(name). Eres un asistente de IA integrado en una app iOS nativa. Responde siempre en español, de forma clara y útil.")
    }
}

struct AIMessageBubble: View {
    let message: AIMessage

    var body: some View {
        HStack {
            if message.role == .user { Spacer() }

            Text(message.content)
                .padding(10)
                .background(message.role == .user ? Color.blue : Color(.systemGray6))
                .foregroundColor(message.role == .user ? .white : .primary)
                .cornerRadius(12)
                .frame(maxWidth: .infinity, alignment: message.role == .user ? .trailing : .leading)

            if message.role == .user { Spacer() }
        }
    }
}

#Preview {
    AIChatView()
}
