export default function Toast({ message }: { message: string }) {
  return (
    <div className="toast toast-enter">
      <span className="toast-dot" />{message}
    </div>
  );
}
